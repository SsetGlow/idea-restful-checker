package com.ssetglow.restfulchecker.config;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public final class ProjectConfigResolver {
    private static final int MAX_SCAN_DEPTH = 8;

    private ProjectConfigResolver() {
    }

    public static Map<String, String> resolve(Project project) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("server.port", "8080");
        values.put("server.servlet.context-path", "");
        values.put("server.context-path", "");
        values.put("spring.mvc.servlet.path", "");

        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return values;
        }

        Path root = Path.of(basePath);
        if (!Files.isDirectory(root)) {
            return values;
        }

        try (Stream<Path> paths = Files.walk(root, MAX_SCAN_DEPTH)) {
            paths.filter(Files::isRegularFile)
                    .filter(ProjectConfigResolver::isSpringConfigFile)
                    .forEach(path -> loadConfig(path, values));
        } catch (IOException ignored) {
            return values;
        }
        return values;
    }

    public static String contextPath(Map<String, String> values) {
        String current = firstNonBlank(values.get("server.servlet.context-path"), values.get("server.context-path"));
        return current == null ? "" : current;
    }

    public static String servletPath(Map<String, String> values) {
        String current = values.get("spring.mvc.servlet.path");
        return current == null ? "" : current;
    }

    private static boolean isSpringConfigFile(Path path) {
        String name = path.getFileName().toString();
        if (!(name.startsWith("application") || name.startsWith("bootstrap"))) {
            return false;
        }
        return name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void loadConfig(Path path, Map<String, String> values) {
        String name = path.getFileName().toString();
        if (name.endsWith(".properties")) {
            loadProperties(path, values);
        } else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            loadYaml(path, values);
        }
    }

    private static void loadProperties(Path path, Map<String, String> values) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ignored) {
            return;
        }
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
    }

    private static void loadYaml(Path path, Map<String, String> values) {
        try {
            Deque<YamlLevel> stack = new ArrayDeque<>();
            for (String rawLine : Files.readAllLines(path)) {
                String withoutComment = stripComment(rawLine);
                if (withoutComment.trim().isEmpty() || withoutComment.trim().startsWith("-")) {
                    continue;
                }
                int indent = countIndent(withoutComment);
                String line = withoutComment.trim();
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                while (!stack.isEmpty() && indent <= stack.peek().indent()) {
                    stack.pop();
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.isEmpty()) {
                    stack.push(new YamlLevel(indent, key));
                } else {
                    values.put(fullYamlKey(stack, key), unquote(value));
                }
            }
        } catch (IOException ignored) {
            return;
        }
    }

    private static String fullYamlKey(Deque<YamlLevel> stack, String key) {
        StringBuilder builder = new StringBuilder();
        Object[] levels = stack.toArray();
        for (int i = levels.length - 1; i >= 0; i--) {
            YamlLevel level = (YamlLevel) levels[i];
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(level.key());
        }
        if (!builder.isEmpty()) {
            builder.append('.');
        }
        builder.append(key);
        return builder.toString();
    }

    private static String stripComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '#' && !inSingleQuote && !inDoubleQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int countIndent(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }
        return count;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private record YamlLevel(int indent, String key) {
    }
}
