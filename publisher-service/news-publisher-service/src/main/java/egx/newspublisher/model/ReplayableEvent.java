package egx.newspublisher.model;

import java.time.Instant;

public interface ReplayableEvent<T> {

    Instant getReplayDueTime();

    String getPartitionKey();

    void setLagMs(long lagMs);

    T toKafkaValue(Instant publishedAt);
}
