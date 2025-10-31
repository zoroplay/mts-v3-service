package com.sportradar.mbs.sdk.internal.utils;

public class TimeUtils {

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final Exception ignored) {
        }
    }

    public static long nowUtcMillis() {
        return System.currentTimeMillis();
    }
}
