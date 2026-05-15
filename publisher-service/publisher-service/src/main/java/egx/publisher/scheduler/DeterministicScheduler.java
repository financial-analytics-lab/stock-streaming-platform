package egx.publisher.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.publisher.config.AppConfig;
import egx.publisher.model.TradeEvent;

/**
 * Deterministic event-time scheduler for replaying trade events.
 * Events are dispatched in order of their replayDueTime, respecting wall-clock
 * timing based on the configured dispatch interval.
 */
public class DeterministicScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeterministicScheduler.class);

    private final AppConfig.SchedulerConfig config;
    private final List<TradeEvent> allEvents;
    private final PriorityQueue<TradeEvent> eventQueue;

    private final AtomicLong dispatchedCount;
    private final AtomicLong skippedCount;
    private final AtomicLong lagWarnCount;

    private volatile boolean running;

    public DeterministicScheduler(AppConfig.SchedulerConfig config, List<TradeEvent> events) {
        this.config = config;
        this.allEvents = events;
        this.eventQueue = new PriorityQueue<>(events);
        this.dispatchedCount = new AtomicLong(0);
        this.skippedCount = new AtomicLong(0);
        this.lagWarnCount = new AtomicLong(0);
        this.running = false;
    }

    /**
     * Runs the deterministic replay loop.
     * Events are dispatched to the onEvent consumer when their replayDueTime has arrived.
     *
     * @param horizonStart the replay start time
     * @param onEvent consumer callback for each event
     */
    public void run(Instant horizonStart, Consumer<TradeEvent> onEvent) {
        running = true;

        log.info("Starting deterministic scheduler with {} events", allEvents.size());
        log.info("Lag thresholds - Warn: {}ms, Fail: {}ms",
                config.getLagThresholdWarnMs(), config.getLagThresholdFailMs());

        /*
         * FIX:
         * Use a fixed real-world start time and a fixed replay start time.
         *
         * replayNow = replayStart + elapsedRealTime
         *
         * This prevents replay time from accelerating due to repeatedly adding
         * the full elapsed time since the original wall-clock start.
         */
        Instant realStart = Instant.now();
        Instant replayStart = horizonStart;

        long lastLogCount = 0;
        long logInterval = 10000;

        while (running && !eventQueue.isEmpty()) {
            TradeEvent nextEvent = eventQueue.peek();

            if (nextEvent == null) {
                break;
            }

            Instant dueTime = nextEvent.getReplayDueTime();

            Instant now = replayStart.plus(Duration.between(realStart, Instant.now()));

            long waitMillis = dueTime.toEpochMilli() - now.toEpochMilli();

            log.info("Now: {}, Next event due at {}, wait time: {}ms",
                    now, dueTime, waitMillis);

            if (waitMillis > 0) {
                long sleepTime = waitMillis;

                try {
                    log.info("Sleeping for {} ms", sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    log.info("Scheduler interrupted, shutting down");
                    running = false;
                    break;
                }

                continue;
            }

            TradeEvent event = eventQueue.poll();

            if (event != null) {
                long lagMillis = now.toEpochMilli() - dueTime.toEpochMilli();
                event.setLagMs(lagMillis);

                log.info("Dispatching event: {}", event);
                log.info("Event replay due time: {}, actual dispatch time: {}, lag: {}ms",
                        dueTime, now, lagMillis);

                if (lagMillis >= config.getLagThresholdFailMs()) {
                    log.error("Replay lag FAILED threshold: {}ms (threshold: {}ms), event: {}",
                            lagMillis, config.getLagThresholdFailMs(), event);
                } else if (lagMillis >= config.getLagThresholdWarnMs()) {
                    log.warn("Replay lag exceeded warning threshold: {}ms (threshold: {}ms), event: {}",
                            lagMillis, config.getLagThresholdWarnMs(), event);
                    lagWarnCount.incrementAndGet();
                }

                try {
                    onEvent.accept(event);
                    dispatchedCount.incrementAndGet();

                    long dispatched = dispatchedCount.get();

                    if (dispatched - lastLogCount >= logInterval) {
                        double progressPct = (dispatched * 100.0) / allEvents.size();

                        log.info("Progress: {}/{} events ({:.2f}%), lag: {}ms",
                                dispatched, allEvents.size(), progressPct, lagMillis);

                        lastLogCount = dispatched;
                    }
                } catch (Exception e) {
                    log.error("Error dispatching event: {}", e.getMessage(), e);
                }
            }
        }

        long remaining = eventQueue.size();
        skippedCount.addAndGet(remaining);

        log.info("Scheduler run complete. Dispatched: {}, Skipped: {}, Warnings: {}",
                dispatchedCount.get(), skippedCount.get(), lagWarnCount.get());
    }

    /**
     * Stops the scheduler loop gracefully.
     */
    public void stop() {
        running = false;
        log.info("Scheduler stop requested");
    }

    /**
     * Returns the number of events that have been dispatched.
     */
    public long getDispatchedCount() {
        return dispatchedCount.get();
    }

    /**
     * Returns the number of events that were skipped.
     */
    public long getSkippedCount() {
        return skippedCount.get();
    }

    /**
     * Returns the number of lag warnings triggered.
     */
    public long getLagWarningCount() {
        return lagWarnCount.get();
    }

    /**
     * Returns true if the scheduler is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the number of events remaining in the queue.
     */
    public int remainingEvents() {
        return eventQueue.size();
    }
}