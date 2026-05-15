package egx.publisher.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes symbol codes to Kafka partitions using consistent hashing.
 * Provides deterministic partition assignment for symbol affinity.
 */
public class PartitionRouter {

    private static final Logger log = LoggerFactory.getLogger(PartitionRouter.class);

    private final int numPartitions;

    /**
     * Creates a router with the default number of partitions (32).
     */
    public PartitionRouter() {
        this(32);
    }

    /**
     * Creates a router with a specified number of partitions.
     *
     * @param numPartitions the number of partitions to route to
     */
    public PartitionRouter(int numPartitions) {
        if (numPartitions <= 0) {
            throw new IllegalArgumentException("numPartitions must be positive");
        }
        this.numPartitions = numPartitions;
    }

    /**
     * Routes a symbol code to a partition using consistent hashing.
     * The same symbol code will always route to the same partition.
     *
     * @param symbolCode the symbol code to route
     * @return the partition number (0 to numPartitions-1)
     */
    public int route(String symbolCode) {
        if (symbolCode == null || symbolCode.isEmpty()) {
            throw new IllegalArgumentException("symbolCode cannot be null or empty");
        }

        // Use hash code and mod to get consistent partition
        int partition = (symbolCode.hashCode() & 0x7FFFFFFF) % numPartitions;
        log.trace("Routed symbol {} to partition {}", symbolCode, partition);
        return partition;
    }

    /**
     * Returns the configured number of partitions.
     *
     * @return the number of partitions
     */
    public int getNumPartitions() {
        return numPartitions;
    }
}