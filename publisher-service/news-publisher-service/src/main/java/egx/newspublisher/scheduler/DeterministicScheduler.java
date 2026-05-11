package egx.newspublisher.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.newspublisher.config.AppConfig;
import egx.newspublisher.model.ReplayableEvent;

public class DeterministicScheduler<T extends ReplayableEvent<?> & Comparable<T>> {

    private static final Logger log = LoggerFactory.getLogger(DeterministicScheduler.class);

    private final AppConfig.SchedulerConfig config;
    private final List<T> allEvents;
    private final PriorityQueue<T> eventQueue;
    private final AtomicLong dispatchedCount;
    private final AtomicLong skippedCount;
    private final AtomicLong lagWarnCount;
    private volatile boolean running;

    public DeterministicScheduler(AppConfig.SchedulerConfig config, List<T> events) {
        this.config = config;
        this.allEvents = events;
        this.eventQueue = new PriorityQueue<>(events);
        this.dispatchedCount = new AtomicLong(0);
        this.skippedCount = new AtomicLong(0);
        this.lagWarnCount = new AtomicLong(0);
        this.running = false;
    }

    public void run(Instant horizonStart, Consumer<T> onEvent) {
        running = true;

        log.info("Starting deterministic scheduler with {} events", allEvents.size());
        log.info("Lag thresholds - Warn: {}ms, Fail: {}ms",
                config.getLagThresholdWarnMs(), config.getLagThresholdFailMs());

        Instant realStart = Instant.now();
        Instant replayStart = horizonStart;
        long lastLogCount = 0;
        long logInterval = 10000;

        while (running && !eventQueue.isEmpty()) {
            T nextEvent = eventQueue.peek();

            if (nextEvent == null) {
                break;
            }
            log.info("Next event: {}", nextEvent);
            Instant dueTime = nextEvent.getReplayDueTime();
            Instant now = replayStart.plus(Duration.between(realStart, Instant.now()));
            long waitMillis = dueTime.toEpochMilli() - now.toEpochMilli();

            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException e) {
                    log.info("Scheduler interrupted, shutting down");
                    running = false;
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            T event = eventQueue.poll();
            if (event != null) {
                long lagMillis = now.toEpochMilli() - dueTime.toEpochMilli();
                event.setLagMs(lagMillis);

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

    public void stop() {
        running = false;
        log.info("Scheduler stop requested");
    }

    public long getDispatchedCount() {
        return dispatchedCount.get();
    }

    public long getSkippedCount() {
        return skippedCount.get();
    }

    public long getLagWarningCount() {
        return lagWarnCount.get();
    }

    public boolean isRunning() {
        return running;
    }

    public int remainingEvents() {
        return eventQueue.size();
    }
}
