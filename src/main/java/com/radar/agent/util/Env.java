package com.radar.agent.util;

public final class Env {
    private Env() {}

    public static String get(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }
}