package com.ssetglow.restfulchecker.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyValueParser {
    private KeyValueParser() {
    }

    public static Map<String, String> parseLines(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            int colon = line.indexOf(':');
            int separator;
            if (equals < 0) {
                separator = colon;
            } else if (colon < 0) {
                separator = equals;
            } else {
                separator = Math.min(equals, colon);
            }
            if (separator < 0) {
                values.put(line, "");
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    public static String formatLines(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return builder.toString();
    }
}
