package com.ssetglow.restfulchecker.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathUtil {
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}/]+)}");

    private PathUtil() {
    }

    public static String joinPath(String... parts) {
        List<String> normalized = new ArrayList<>();
        if (parts != null) {
            for (String part : parts) {
                if (part == null) {
                    continue;
                }
                String value = part.trim();
                if (value.isEmpty() || "/".equals(value)) {
                    continue;
                }
                while (value.startsWith("/")) {
                    value = value.substring(1);
                }
                while (value.endsWith("/")) {
                    value = value.substring(0, value.length() - 1);
                }
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        if (normalized.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", normalized);
    }

    public static String joinUrl(String host, String path) {
        String normalizedHost = host == null ? "" : host.trim();
        String normalizedPath = path == null || path.isBlank() ? "/" : path.trim();
        while (normalizedHost.endsWith("/")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedHost + normalizedPath;
    }

    public static List<String> extractPathVariables(String path) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = PATH_VARIABLE.matcher(path == null ? "" : path);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.contains(name)) {
                variables.add(name);
            }
        }
        return variables;
    }

    public static String replacePathVariables(String path, Map<String, String> values) {
        Matcher matcher = PATH_VARIABLE.matcher(path == null ? "" : path);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(urlEncode(value)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String appendQuery(String url, Map<String, String> queryValues) {
        if (queryValues.isEmpty()) {
            return url;
        }
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : queryValues.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            pairs.add(urlEncode(entry.getKey().trim()) + "=" + urlEncode(entry.getValue() == null ? "" : entry.getValue()));
        }
        if (pairs.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + String.join("&", pairs);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
