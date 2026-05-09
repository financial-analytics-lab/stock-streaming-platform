package com.stockstream.candle.topology;

import com.stockstream.candle.model.Candle;
import com.stockstream.candle.model.CandleInterval;
import com.stockstream.candle.serializer.SerializerFactory;
import com.stockstream.core.model.Tick;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CandleTopologyTest {

    private static final String INPUT = "stock-ticks";

    private TopologyTestDriver driver;
    private TestInputTopic<String, Tick> inputTopic;
    private TestOutputTopic<String, Candle> output1s;
    private TestOutputTopic<String, Candle> output1m;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-candle-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");

        driver = new TopologyTestDriver(
                new CandleTopologyBuilder(INPUT).build().build(), props
        );

        inputTopic = driver.createInputTopic(
                INPUT,
                Serdes.String().serializer(),
                SerializerFactory.tickSerializer().serializer()
        );
        output1s = driver.createOutputTopic(
                CandleInterval.ONE_SECOND.outputTopic,
                Serdes.String().deserializer(),
                SerializerFactory.candleSerializer().deserializer()
        );
        output1m = driver.createOutputTopic(
                CandleInterval.ONE_MINUTE.outputTopic,
                Serdes.String().deserializer(),
                SerializerFactory.candleSerializer().deserializer()
        );
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void singleTick_emitsCorrectCandle_after1sWindowCloses() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        pipe("AAPL", 1L, "150.00", 1000L, t0);

        // Advance stream time past window(1s) + grace(2s) = 3s minimum
        pipe("AAPL", 99L, "150.00", 0L, t0.plusSeconds(4));

        assertFalse(output1s.isEmpty(), "1s candle must be emitted");
        Candle c = output1s.readValue();

        assertEquals("AAPL", c.symbol());
        assertEquals("1s", c.interval());
        assertEquals(0, new BigDecimal("150.00").compareTo(c.open()));
        assertEquals(0, new BigDecimal("150.00").compareTo(c.high()));
        assertEquals(0, new BigDecimal("150.00").compareTo(c.low()));
        assertEquals(0, new BigDecimal("150.00").compareTo(c.close()));
        assertEquals(1000L, c.volume());
        assertEquals(1L, c.tickCount());
        assertEquals(t0, c.windowStart());
        assertEquals(t0.plusSeconds(1), c.windowEnd());
    }

    @Test
    void multipleTicks_inSameWindow_computesCorrectOHLCV() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        String sym = "MSFT";

        pipe(sym, 1L, "200.00", 100L, t0);
        pipe(sym, 2L, "205.00", 200L, t0.plusMillis(200));
        pipe(sym, 3L, "198.00", 150L, t0.plusMillis(600));
        pipe(sym, 4L, "202.00", 300L, t0.plusMillis(900));

        pipe(sym, 99L, "202.00", 0L, t0.plusSeconds(4));

        assertFalse(output1s.isEmpty());
        Candle c = output1s.readValue();

        assertEquals(0, new BigDecimal("200.00").compareTo(c.open()),  "open = first tick");
        assertEquals(0, new BigDecimal("205.00").compareTo(c.high()),  "high = max price");
        assertEquals(0, new BigDecimal("198.00").compareTo(c.low()),   "low = min price");
        assertEquals(0, new BigDecimal("202.00").compareTo(c.close()), "close = last tick");
        assertEquals(750L, c.volume(), "volume = sum");
        assertEquals(4L, c.tickCount());
    }

    @Test
    void lateTickWithinGrace_isIncluded() {
        // Grace period covers ticks whose *event timestamp* is within the window
        // but which arrive at the processor after stream time passed the window end.
        // We simulate this by first advancing stream time past window end, then
        // piping a tick whose event time is still inside the window.
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        String sym = "GOOG";

        // First tick — inside [t0, t0+1s)
        pipe(sym, 1L, "2800.00", 10L, t0.plusMillis(200));

        // Advance stream time to t0+1.5s (past window end but within 2s grace)
        pipe(sym, 2L, "2800.00", 0L, t0.plusMillis(1500));

        // Late tick: event time is t0+800ms (inside the closed window), arrives now
        pipe(sym, 3L, "2850.00", 5L, t0.plusMillis(800));

        // Advance past full grace period to trigger suppress flush
        pipe(sym, 99L, "2800.00", 0L, t0.plusSeconds(4));

        Candle c = output1s.readValue();
        assertEquals(0, new BigDecimal("2850.00").compareTo(c.high()),
                "late tick within grace must be included — got: " + c.high());
        assertEquals(2L, c.tickCount());
    }

    @Test
    void differentSymbols_produceSeparateCandles() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");

        pipe("AAPL", 1L, "150.00", 1000L, t0);
        pipe("MSFT", 2L, "280.00", 500L,  t0.plusMillis(100));

        // Advance time for both symbols
        pipe("AAPL", 99L, "150.00", 0L, t0.plusSeconds(4));
        pipe("MSFT", 99L, "280.00", 0L, t0.plusSeconds(4));

        List<org.apache.kafka.streams.KeyValue<String, Candle>> candles = output1s.readKeyValuesToList();
        assertEquals(2, candles.size());

        Map<String, Candle> bySymbol = candles.stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));

        assertEquals("AAPL", bySymbol.get("AAPL").symbol());
        assertEquals("MSFT", bySymbol.get("MSFT").symbol());
        assertEquals(0, new BigDecimal("150.00").compareTo(bySymbol.get("AAPL").open()));
        assertEquals(0, new BigDecimal("280.00").compareTo(bySymbol.get("MSFT").open()));
    }

    @Test
    void noOutputBeforeWindowCloses() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");

        // Send ticks inside the 1s window — nothing should emit yet
        pipe("AAPL", 1L, "150.00", 100L, t0);
        pipe("AAPL", 2L, "151.00", 100L, t0.plusMillis(500));

        assertTrue(output1s.isEmpty(), "suppress must hold output until window + grace elapses");
    }

    @Test
    void windowBoundsAreCorrectForOneMinute() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        pipe("TSLA", 1L, "900.00", 50L, t0.plusSeconds(30));

        // Advance past 1m window + 10s grace = 70s
        pipe("TSLA", 99L, "900.00", 0L, t0.plusSeconds(80));

        assertFalse(output1m.isEmpty());
        Candle c = output1m.readValue();
        assertEquals(t0, c.windowStart());
        assertEquals(t0.plusSeconds(60), c.windowEnd());
    }

    // helper
    private void pipe(String symbol, long id, String price, long volume, Instant eventTime) {
        Tick t = new Tick(id, symbol, new BigDecimal(price), volume, eventTime, eventTime);
        inputTopic.pipeInput(symbol, t, eventTime.toEpochMilli());
    }
}
