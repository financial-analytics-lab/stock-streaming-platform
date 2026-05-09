package com.stockstream.candle.config;

import com.stockstream.core.exception.ConsumerConfigException;
import com.stockstream.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public final class CandleConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CandleConfigLoader.class);
    private static final String RESOURCE = "candle-processor.properties";

    private CandleConfigLoader() {}

    public static CandleProcessorConfig load() {
        return load(RESOURCE);
    }

    public static CandleProcessorConfig load(String resourceName) {
        Properties file = loadFromClasspath(resourceName);

        String bootstrap       = resolve(file, "bootstrap.servers",  "CANDLE_BOOTSTRAP_SERVERS");
        String appId           = resolve(file, "application.id",     "CANDLE_APPLICATION_ID");
        String inputTopic      = resolve(file, "input.topic",        "CANDLE_INPUT_TOPIC");
        String stateDir        = resolve(file, "state.dir",          "CANDLE_STATE_DIR");
        String threadsStr      = resolve(file, "num.stream.threads", "CANDLE_NUM_STREAM_THREADS");
        String autoOffsetReset = resolve(file, "auto.offset.reset",  "CANDLE_AUTO_OFFSET_RESET");

        int threads = threadsStr != null
                ? Integer.parseInt(threadsStr)
                : CandleProcessorConfig.DEFAULT_THREADS;

        return new CandleProcessorConfig(bootstrap, appId, inputTopic, threads, stateDir, autoOffsetReset);
    }

    private static String resolve(Properties file, String key, String envKey) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env;
        return file.getProperty(key);
    }

    private static Properties loadFromClasspath(String name) {
        Properties p = new Properties();
        try (InputStream is = CandleConfigLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (is != null) {
                p.load(is);
                LOG.info("Loaded config from classpath: " + name);
            } else {
                LOG.info("No classpath resource '" + name + "'; relying on environment variables");
            }
        } catch (IOException e) {
            throw new ConsumerConfigException("Failed to load " + name, e);
        }
        return p;
    }
}
