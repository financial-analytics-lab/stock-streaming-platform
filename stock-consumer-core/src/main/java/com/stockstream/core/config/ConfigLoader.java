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

    private ConfigLoader() {}

    /**
     * Load config from classpath resource "consumer.properties"
     * merged with environment variables. Env vars win.
     */
    public static ConsumerConfig load() {
        return load("consumer.properties");
    }

    public static ConsumerConfig load(String resourceName) {
        Properties fileProps = loadFromClasspath(resourceName);

        String bootstrapServers = resolve(fileProps, "bootstrap.servers", "STOCK_CONSUMER_BOOTSTRAP_SERVERS");
        String groupId = resolve(fileProps, "group.id", "STOCK_CONSUMER_GROUP_ID");
        String topic = resolve(fileProps, "topic", "STOCK_CONSUMER_TOPIC");
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
                groupId,
                topic,
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
