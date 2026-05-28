package com.ssetglow.restfulchecker.util;

import java.util.ArrayDeque;
import java.util.Deque;

public final class JsonBodyFormatter {
    private JsonBodyFormatter() {
    }

    public static String formatIfJson(String contentType, String body) {
        if (body == null || body.isBlank()) {
            return body == null ? "" : body;
        }
        String trimmed = body.trim();
        boolean declaredJson = contentType != null && contentType.toLowerCase().contains("json");
        boolean looksJson = (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
        if (!declaredJson && !looksJson) {
            return body;
        }
        String formatted = prettyPrint(trimmed);
        return formatted == null ? body : formatted;
    }

    private static String prettyPrint(String json) {
        StringBuilder builder = new StringBuilder();
        Deque<Character> expectedClosers = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        int indent = 0;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                builder.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (Character.isWhitespace(ch)) {
                continue;
            }
            switch (ch) {
                case '"' -> {
                    inString = true;
                    builder.append(ch);
                }
                case '{', '[' -> {
                    char closer = ch == '{' ? '}' : ']';
                    int next = nextNonWhitespace(json, i + 1);
                    if (next >= 0 && json.charAt(next) == closer) {
                        builder.append(ch).append(closer);
                        i = next;
                    } else {
                        expectedClosers.push(closer);
                        builder.append(ch);
                        indent++;
                        appendNewLine(builder, indent);
                    }
                }
                case '}', ']' -> {
                    if (expectedClosers.isEmpty() || expectedClosers.pop() != ch) {
                        return null;
                    }
                    indent--;
                    appendNewLine(builder, indent);
                    builder.append(ch);
                }
                case ',' -> {
                    builder.append(ch);
                    appendNewLine(builder, indent);
                }
                case ':' -> builder.append(": ");
                default -> builder.append(ch);
            }
        }
        if (inString || !expectedClosers.isEmpty()) {
            return null;
        }
        return builder.toString();
    }

    private static int nextNonWhitespace(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void appendNewLine(StringBuilder builder, int indent) {
        builder.append('\n');
        builder.append("  ".repeat(Math.max(indent, 0)));
    }
}
