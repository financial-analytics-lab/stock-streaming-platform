package egx.newspublisher.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    private ReplayConfig replay;
    private KafkaConfig kafka;
    private SchedulerConfig scheduler;
    private NewsConfig news;

    public static AppConfig load(String configPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper.readValue(new File(configPath), AppConfig.class);
    }

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

    public NewsConfig getNews() {
        return news;
    }

    public void setNews(NewsConfig news) {
        this.news = news;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReplayConfig {
        private String horizonStart;
        private String singleDate;

        public String getHorizonStart() {
            return horizonStart;
        }

        public void setHorizonStart(String horizonStart) {
            this.horizonStart = horizonStart;
        }

        public String getSingleDate() {
            return singleDate;
        }

        public void setSingleDate(String singleDate) {
            this.singleDate = singleDate;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KafkaConfig {
        private String bootstrapServers;
        private String topicPrefix;
        private int lingerMs = 5;
        private int batchSize = 16384;
        private String acks = "1";
        private int maxInFlight = 100;
        private int partitions = 32;

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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchedulerConfig {
        private int threadPoolSize = 8;
        private int dispatchIntervalMs = 10;
        private int lagThresholdWarnMs = 1000;
        private int lagThresholdFailMs = 4000;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsConfig {
        private String filePath;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}
