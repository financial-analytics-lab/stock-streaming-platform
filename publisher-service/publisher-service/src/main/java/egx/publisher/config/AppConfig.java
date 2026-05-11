package egx.publisher.config;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Application configuration loaded from config/application.yaml.
 * Contains nested config objects for replay, kafka, scheduler, and symbols.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private ReplayConfig replay;
    private KafkaConfig kafka;
    private SchedulerConfig scheduler;
    private SymbolsConfig symbols;
    private MetricsConfig metrics;

    public AppConfig() {}

    /**
     * Load configuration from a YAML file.
     *
     * @param configPath path to the YAML config file (relative or absolute)
     * @return populated AppConfig instance
     * @throws IOException if the file cannot be read or parsed
     */
    public static AppConfig load(String configPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper.readValue(new File(configPath), AppConfig.class);
    }

    // ─── Nested config objects ─────────────────────────────────────────────────

    public ReplayConfig getReplay() {
        return replay;
    }

    public void setReplay(ReplayConfig replay) {
        this.replay = replay;
    }

    public KafkaConfig getKafka() {
        return kafka;
    }

    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    public SchedulerConfig getScheduler() {
        return scheduler;
    }

    public void setScheduler(SchedulerConfig scheduler) {
        this.scheduler = scheduler;
    }

    public SymbolsConfig getSymbols() {
        return symbols;
    }

    public void setSymbols(SymbolsConfig symbols) {
        this.symbols = symbols;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
    }

    // ─── Replay config ─────────────────────────────────────────────────────────

    public static class ReplayConfig {
        private String horizonStart;
        private String artifactPath;
        private String scalePolicy;
        private String singleSessionDate; // e.g. "20250102" — loads only this session, ignores horizonStart

        public String getHorizonStart() {
            return horizonStart;
        }

        public void setHorizonStart(String horizonStart) {
            this.horizonStart = horizonStart;
        }

        public String getArtifactPath() {
            return artifactPath;
        }

        public void setArtifactPath(String artifactPath) {
            this.artifactPath = artifactPath;
        }

        public String getScalePolicy() {
            return scalePolicy;
        }

        public void setScalePolicy(String scalePolicy) {
            this.scalePolicy = scalePolicy;
        }

        public String getSingleSessionDate() {
            return singleSessionDate;
        }

        public void setSingleSessionDate(String singleSessionDate) {
            this.singleSessionDate = singleSessionDate;
        }
    }

    // ─── Kafka config ─────────────────────────────────────────────────────────

    public static class KafkaConfig {
        private String bootstrapServers;
        private String topicPrefix;
        private int lingerMs = 5;
        private int batchSize = 16384;
        private String acks = "1";
        private int maxInFlight = 100;
        private int partitions = 32;   // number of Kafka partitions per topic

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTopicPrefix() {
            return topicPrefix;
        }

        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }

        public int getLingerMs() {
            return lingerMs;
        }

        public void setLingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }

        public int getMaxInFlight() {
            return maxInFlight;
        }

        public void setMaxInFlight(int maxInFlight) {
            this.maxInFlight = maxInFlight;
        }

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        /**
         * Build Kafka topic name for a given symbol code.
         * Format: {topic_prefix}-{symbol_code}
         * Example: egx30-EGS30901C010
         */
        public String buildTopicName(String symbolCode) {
            return topicPrefix + "-" + symbolCode;
        }
    }

    // ─── Scheduler config ─────────────────────────────────────────────────────

    public static class SchedulerConfig {
        private int threadPoolSize = 8;
        private int dispatchIntervalMs = 10;
        private int lagThresholdWarnMs = 100;
        private int lagThresholdFailMs = 1000;

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }

        public int getDispatchIntervalMs() {
            return dispatchIntervalMs;
        }

        public void setDispatchIntervalMs(int dispatchIntervalMs) {
            this.dispatchIntervalMs = dispatchIntervalMs;
        }

        public int getLagThresholdWarnMs() {
            return lagThresholdWarnMs;
        }

        public void setLagThresholdWarnMs(int lagThresholdWarnMs) {
            this.lagThresholdWarnMs = lagThresholdWarnMs;
        }

        public int getLagThresholdFailMs() {
            return lagThresholdFailMs;
        }

        public void setLagThresholdFailMs(int lagThresholdFailMs) {
            this.lagThresholdFailMs = lagThresholdFailMs;
        }
    }

    // ─── Symbols config ────────────────────────────────────────────────────────

    public static class SymbolsConfig {
        private String mappingFile;

        public String getMappingFile() {
            return mappingFile;
        }

        public void setMappingFile(String mappingFile) {
            this.mappingFile = mappingFile;
        }
    }

    // ─── Metrics config ─────────────────────────────────────────────────────────

    /**
     * Configuration for the benchmark metrics collector.
     * Controls where per-event timing data is written.
     */
    public static class MetricsConfig {
        /** Directory where the CSV metrics file will be written. Default: "metrics". */
        private String outputDir = "metrics";

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }
    }
}
