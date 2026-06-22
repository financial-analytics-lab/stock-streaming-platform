package com.stockstream.dashboard.config;

import java.time.*;

public class SessionConfig {
    public static final LocalTime SESSION_OPEN  = LocalTime.of(10, 0);
    public static final LocalTime SESSION_CLOSE = LocalTime.of(14, 0);
    public static final Duration RETENTION     = Duration.ofHours(4);
    public static final ZoneId MARKET_ZONE   = ZoneId.of("Africa/Cairo");


    public static Instant retentionCutoff() {
        ZonedDateTime now         = ZonedDateTime.now(MARKET_ZONE);
        ZonedDateTime sessionOpen = now.toLocalDate()
                .atTime(SESSION_OPEN)
                .atZone(MARKET_ZONE);
        ZonedDateTime rollingCutoff = now.minus(RETENTION);

        // Take the later of the two — never go before session open
        return rollingCutoff.isAfter(sessionOpen)
                ? rollingCutoff.toInstant()
                : sessionOpen.toInstant();
    }

    public static boolean isSessionActive() {
        LocalTime now = LocalTime.now(MARKET_ZONE);
        return !now.isBefore(SESSION_OPEN) && !now.isAfter(SESSION_CLOSE);
    }



}
