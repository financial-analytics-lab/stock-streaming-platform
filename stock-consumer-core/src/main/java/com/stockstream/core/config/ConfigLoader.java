package com.stockstream.core.config;

import com.stockstream.core.exception.ConsumerConfigException;
import com.stockstream.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;

public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    // Defaults applied when neither env var nor properties file provides a value.
    static final String DEFAULT_TICK_TOPIC = "stock-ticks";
    static final String DEFAULT_TICK_GROUP_ID = "dashboard-tick-consumer";
    static final String DEFAULT_NEWS_TOPIC = "news-events";
    static final String DEFAULT_NEWS_GROUP_ID = "dashboard-news-consumer";

    private ConfigLoader() {}

    /** Load broker/poll settings. Topic and group are loaded separately, see loadXxxSubscription. */
    public static ConsumerConfig load() {
        return load("consumer.properties");
    }

    public static ConsumerConfig load(String resourceName) {
        Properties fileProps = loadFromClasspath(resourceName);
        return buildConfig(fileProps);
    }

    /** Load the tick subscription (topic + group). */
    public static Subscription loadTickSubscription() {
        return loadTickSubscription("consumer.properties");
    }

    public static Subscription loadTickSubscription(String resourceName) {
        Properties fileProps = loadFromClasspath(resourceName);
        String topic = resolve(fileProps, "tick.topic", "STOCK_CONSUMER_TICK_TOPIC");
        String groupId = resolve(fileProps, "tick.group.id", "STOCK_CONSUMER_TICK_GROUP_ID");
        if (topic == null) topic = DEFAULT_TICK_TOPIC;
        if (groupId == null) groupId = DEFAULT_TICK_GROUP_ID;
        return new Subscription(topic, groupId);
    }

    /** Load the news subscription (topic + group). */
    public static Subscription loadNewsSubscription() {
        return loadNewsSubscription("consumer.properties");
    }

    public static Subscription loadNewsSubscription(String resourceName) {
        Properties fileProps = loadFromClasspath(resourceName);
        String topic = resolve(fileProps, "news.topic", "STOCK_CONSUMER_NEWS_TOPIC");
        String groupId = resolve(fileProps, "news.group.id", "STOCK_CONSUMER_NEWS_GROUP_ID");
        if (topic == null) topic = DEFAULT_NEWS_TOPIC;
        if (groupId == null) groupId = DEFAULT_NEWS_GROUP_ID;
        return new Subscription(topic, groupId);
    }

    // ---- internals ----

    private static ConsumerConfig buildConfig(Properties fileProps) {
        String bootstrapServers = resolve(fileProps, "bootstrap.servers", "STOCK_CONSUMER_BOOTSTRAP_SERVERS");
        String pollTimeoutMs = resolve(fileProps, "poll.timeout.ms", "STOCK_CONSUMER_POLL_TIMEOUT_MS");
        String autoOffsetReset = resolve(fileProps, "auto.offset.reset", "STOCK_CONSUMER_AUTO_OFFSET_RESET");
        String maxPollRecords = resolve(fileProps, "max.poll.records", "STOCK_CONSUMER_MAX_POLL_RECORDS");

        Duration pollTimeout = pollTimeoutMs != null
                ? Duration.ofMillis(Long.parseLong(pollTimeoutMs))
                : null;

        int maxPoll = maxPollRecords != null
                ? Integer.parseInt(maxPollRecords)
                : ConsumerConfig.DEFAULT_MAX_POLL_RECORDS;

        return new ConsumerConfig(
                bootstrapServers,
                pollTimeout,
                false,
                autoOffsetReset,
                maxPoll,
                new Properties()
        );
    }

    private static String resolve(Properties fileProps, String propKey, String envKey) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            LOG.fine(() -> "Config '" + propKey + "' resolved from env var " + envKey);
            return envVal;
        }
        String fileVal = fileProps.getProperty(propKey);
        if (fileVal != null && !fileVal.isBlank()) {
            LOG.fine(() -> "Config '" + propKey + "' resolved from properties file");
            return fileVal;
        }
        return null;
    }

    private static Properties loadFromClasspath(String resourceName) {
        Properties props = new Properties();
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is != null) {
                props.load(is);
                LOG.info("Loaded config from classpath: " + resourceName);
            } else {
                LOG.info("No classpath resource '" + resourceName + "' found; using env vars only");
            }
        } catch (IOException e) {
            throw new ConsumerConfigException("Failed to load " + resourceName, e);
        }
        return props;
    }
}
