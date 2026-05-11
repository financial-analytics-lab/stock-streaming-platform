package egx.publisher.service.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration holder for publisher service settings.
 * Loads settings from classpath: publisher-service.properties
 */
public class ServiceConfig {

    private final Properties properties;

    public ServiceConfig() {
        this.properties = new Properties();
        loadDefaults();
    }

    public ServiceConfig(String configPath) {
        this.properties = new Properties();
        loadDefaults();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configPath)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config from " + configPath, e);
        }
    }

    private void loadDefaults() {
        properties.setProperty("kafka.bootstrap.servers", "localhost:9092");
        properties.setProperty("kafka.topic.prefix", "egx-market-data");
        properties.setProperty("replay.speed", "1.0");
        properties.setProperty("shards.path", "/home/abdallah/Desktop/Grad Project/new pub sub/publisher-spark/data/shards");
        properties.setProperty("scheduler.batch.interval.ms", "1000");
        properties.setProperty("scheduler.max.delay.ms", "5000");
    }

    public String get(String key) { return properties.getProperty(key); }
    public String get(String key, String defaultValue) { return properties.getProperty(key, defaultValue); }
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }
    public void set(String key, String value) { properties.setProperty(key, value); }
    public Properties toProperties() { return new Properties(properties); }

    public String getKafkaBootstrapServers() { return get("kafka.bootstrap.servers"); }
    public String getKafkaTopicPrefix() { return get("kafka.topic.prefix"); }
    public double getReplaySpeed() { return getDouble("replay.speed", 1.0); }
    public String getShardsPath() { return get("shards.path"); }
    public long getSchedulerBatchIntervalMs() { return getInt("scheduler.batch.interval.ms", 1000); }
    public long getSchedulerMaxDelayMs() { return getInt("scheduler.max.delay.ms", 5000); }
}
