package com.radar.agent.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private Json() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON tree", e);
        }
    }

    public static com.fasterxml.jackson.databind.JsonNode readTreeLenient(String text) {
        try {
            return readTree(text);
        } catch (RuntimeException ex) {
            String extracted = extractJson(text);
            if (extracted == null) {
                throw ex;
            }
            return readTree(extracted);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to write JSON", e);
        }
    }

    private static String extractJson(String text) {
        if (text == null) {
            return null;
        }
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int brace = 0;
        int bracket = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') brace++;
                if (c == '}') brace--;
                if (c == '[') bracket++;
                if (c == ']') bracket--;
                if (brace == 0 && bracket == 0 && (c == '}' || c == ']')) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
