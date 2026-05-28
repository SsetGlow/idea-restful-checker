package com.ssetglow.restfulchecker.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private PlaceholderResolver() {
    }

    public static String resolve(String template, Map<String, String> variables) {
        String result = template == null ? "" : template;
        for (int i = 0; i < 5; i++) {
            String resolved = resolveOnce(result, variables);
            if (resolved.equals(result)) {
                return resolved;
            }
            result = resolved;
        }
        return result;
    }

    private static String resolveOnce(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String fallback = matcher.group(2);
            String value = resolveValue(key, variables);
            if (value == null) {
                value = fallback;
            }
            if (value == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String resolveValue(String key, Map<String, String> variables) {
        if (key.startsWith("env.")) {
            return System.getenv(key.substring("env.".length()));
        }
        if (variables == null) {
            return null;
        }
        return variables.get(key);
    }
}
