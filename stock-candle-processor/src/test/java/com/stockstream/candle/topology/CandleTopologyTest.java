package com.stockstream.candle.topology;

import com.stockstream.candle.model.CandleInterval;
import com.stockstream.candle.serializer.SerializerFactory;
import com.stockstream.core.model.Candle;
import com.stockstream.core.model.CandleStatus;
import com.stockstream.core.model.Tick;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
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

        // Drain OPEN updates, find the CLOSED one
        Candle c = readClosed(output1s);
        assertNotNull(c, "closed 1s candle must be emitted");

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
        assertEquals(CandleStatus.CLOSED, c.status());
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

        Candle c = readClosed(output1s);
        assertNotNull(c);

        assertEquals(0, new BigDecimal("200.00").compareTo(c.open()),  "open = first tick");
        assertEquals(0, new BigDecimal("205.00").compareTo(c.high()),  "high = max price");
        assertEquals(0, new BigDecimal("198.00").compareTo(c.low()),   "low = min price");
        assertEquals(0, new BigDecimal("202.00").compareTo(c.close()), "close = last tick");
        assertEquals(750L, c.volume(), "volume = sum");
        assertEquals(4L, c.tickCount());
        assertEquals(CandleStatus.CLOSED, c.status());
    }

    @Test
    void lateTickWithinGrace_isIncluded() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        String sym = "GOOG";

        pipe(sym, 1L, "2800.00", 10L, t0.plusMillis(200));
        pipe(sym, 2L, "2800.00", 0L,  t0.plusMillis(1500));
        pipe(sym, 3L, "2850.00", 5L,  t0.plusMillis(800));
        pipe(sym, 99L, "2800.00", 0L, t0.plusSeconds(4));

        Candle c = readClosed(output1s);
        assertNotNull(c);
        assertEquals(0, new BigDecimal("2850.00").compareTo(c.high()),
                "late tick within grace must be included — got: " + c.high());
        assertEquals(2L, c.tickCount());
        assertEquals(CandleStatus.CLOSED, c.status());
    }

    @Test
    void differentSymbols_produceSeparateCandles() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");

        pipe("AAPL", 1L, "150.00", 1000L, t0);
        pipe("MSFT", 2L, "280.00", 500L,  t0.plusMillis(100));
        pipe("AAPL", 99L, "150.00", 0L, t0.plusSeconds(4));
        pipe("MSFT", 99L, "280.00", 0L, t0.plusSeconds(4));

        // Read all records, keep only CLOSED ones, key by symbol
        List<KeyValue<String, Candle>> all = output1s.readKeyValuesToList();
        Map<String, Candle> bySymbol = all.stream()
                .filter(kv -> kv.value.status() == CandleStatus.CLOSED)
                .collect(Collectors.toMap(kv -> kv.value.symbol(), kv -> kv.value));

        assertEquals(2, bySymbol.size());
        assertEquals("AAPL", bySymbol.get("AAPL").symbol());
        assertEquals("MSFT", bySymbol.get("MSFT").symbol());
        assertEquals(0, new BigDecimal("150.00").compareTo(bySymbol.get("AAPL").open()));
        assertEquals(0, new BigDecimal("280.00").compareTo(bySymbol.get("MSFT").open()));
    }

    @Test
    void liveCandle_emitsOpenUpdatesOnEveryTick() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");

        pipe("AAPL", 1L, "100.00", 10L, t0);
        pipe("AAPL", 2L, "105.00", 20L, t0.plusMillis(300));
        pipe("AAPL", 3L, "102.00", 15L, t0.plusMillis(700));

        // Read all records emitted so far (no time advance — no CLOSED yet)
        List<KeyValue<String, Candle>> records = output1s.readKeyValuesToList();

        // All 3 ticks should have produced OPEN updates
        assertTrue(records.size() >= 3, "expected at least 3 OPEN updates, got " + records.size());
        assertTrue(records.stream().allMatch(kv -> kv.value.status() == CandleStatus.OPEN),
                "all records before window closes must be OPEN");

        // Key must be composite: symbol:windowStart
        String key = records.get(0).key;
        assertTrue(key.startsWith("AAPL:"), "key must start with symbol, got: " + key);

        // OHLCV of last OPEN update should reflect all 3 ticks
        Candle latest = records.get(records.size() - 1).value;
        assertEquals(0, new BigDecimal("100.00").compareTo(latest.open()),  "open = first tick");
        assertEquals(0, new BigDecimal("105.00").compareTo(latest.high()),  "high = max");
        assertEquals(0, new BigDecimal("102.00").compareTo(latest.close()), "close = last tick");
        assertEquals(3L, latest.tickCount());
    }

    @Test
    void closedCandle_followsOpenUpdates_forSameKey() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");

        pipe("AAPL", 1L, "150.00", 100L, t0);
        pipe("AAPL", 2L, "155.00", 50L,  t0.plusMillis(500));

        // Advance past window + grace
        pipe("AAPL", 99L, "150.00", 0L, t0.plusSeconds(4));

        List<KeyValue<String, Candle>> all = output1s.readKeyValuesToList();

        // Must have both OPEN and CLOSED records for the same key
        List<Candle> opens  = all.stream().filter(kv -> kv.value.status() == CandleStatus.OPEN).map(kv -> kv.value).collect(Collectors.toList());
        List<Candle> closed = all.stream().filter(kv -> kv.value.status() == CandleStatus.CLOSED).map(kv -> kv.value).collect(Collectors.toList());

        assertFalse(opens.isEmpty(),  "must have OPEN updates");
        assertEquals(1, closed.size(), "must have exactly one CLOSED candle");

        // CLOSED candle must reflect all real ticks (not the sentinel)
        Candle fin = closed.get(0);
        assertEquals(0, new BigDecimal("150.00").compareTo(fin.open()));
        assertEquals(0, new BigDecimal("155.00").compareTo(fin.close()));
        assertEquals(CandleStatus.CLOSED, fin.status());

        // OPEN updates and the CLOSED candle for the real window share the same key
        String closedKey = all.stream()
                .filter(kv -> kv.value.status() == CandleStatus.CLOSED
                        && kv.value.tickCount() > 0)
                .findFirst().get().key;
        assertTrue(opens.stream().anyMatch(c -> {
            String openKey = all.stream()
                    .filter(kv -> kv.value == c).findFirst().get().key;
            return openKey.equals(closedKey);
        }), "at least one OPEN update must share the key with the CLOSED candle");
    }

    @Test
    void windowBoundsAreCorrectForOneMinute() {
        Instant t0 = Instant.parse("2026-01-15T10:00:00Z");
        pipe("TSLA", 1L, "900.00", 50L, t0.plusSeconds(30));

        // Advance past 1m window + 10s grace = 70s
        pipe("TSLA", 99L, "900.00", 0L, t0.plusSeconds(80));

        Candle c = readClosed(output1m);
        assertNotNull(c);
        assertEquals(t0, c.windowStart());
        assertEquals(t0.plusSeconds(60), c.windowEnd());
        assertEquals(CandleStatus.CLOSED, c.status());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void pipe(String symbol, long id, String price, long volume, Instant eventTime) {
        Tick t = new Tick(id, symbol, symbol, new BigDecimal(price), volume, eventTime, eventTime, 0L);
        inputTopic.pipeInput(symbol, t, eventTime.toEpochMilli());
    }

    /** Drains the topic and returns the first CLOSED candle, or null if none. */
    private Candle readClosed(TestOutputTopic<String, Candle> topic) {
        return topic.readKeyValuesToList().stream()
                .map(kv -> kv.value)
                .filter(c -> c.status() == CandleStatus.CLOSED)
                .findFirst()
                .orElse(null);
    }
}
