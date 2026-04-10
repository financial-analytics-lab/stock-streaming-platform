package com.stockstream.core.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class LoggerFactory {

    private static volatile boolean initialized = false;

    private LoggerFactory() {}

    public static Logger getLogger(Class<?> clazz) {
        if (!initialized) {
            initializeLogging();
        }
        return Logger.getLogger(clazz.getName());
    }

    private static synchronized void initializeLogging() {
        if (initialized) return;

        Logger root = Logger.getLogger("com.stockstream");

        String levelStr = System.getenv("STOCK_CONSUMER_LOG_LEVEL");
        Level level = Level.INFO;
        if (levelStr != null) {
            try {
                level = Level.parse(levelStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        root.setLevel(level);

        if (root.getHandlers().length == 0) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(level);
            handler.setFormatter(new SimpleFormatter());
            root.addHandler(handler);
        }
        root.setUseParentHandlers(false);

        initialized = true;
    }
}
