package com.ssetglow.restfulchecker.util;

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
        JsonFormatResult result = formatJson(trimmed);
        return result.valid() ? result.formatted() : body;
    }

    public static JsonFormatResult formatJson(String body) {
        if (body == null || body.isBlank()) {
            return JsonFormatResult.valid("");
        }
        try {
            return JsonFormatResult.valid(new Parser(body.trim()).parse());
        } catch (JsonParseException exception) {
            return JsonFormatResult.invalid(exception.getMessage());
        }
    }

    public record JsonFormatResult(boolean valid, String formatted, String errorMessage) {
        public static JsonFormatResult valid(String formatted) {
            return new JsonFormatResult(true, formatted, "");
        }

        public static JsonFormatResult invalid(String errorMessage) {
            return new JsonFormatResult(false, "", errorMessage);
        }
    }

    private static final class Parser {
        private final String source;
        private final StringBuilder builder = new StringBuilder();
        private int index;
        private int indent;

        private Parser(String source) {
            this.source = source;
        }

        private String parse() {
            skipWhitespace();
            parseValue();
            skipWhitespace();
            if (index < source.length()) {
                error("Unexpected trailing content");
            }
            return builder.toString();
        }

        private void parseValue() {
            skipWhitespace();
            if (index >= source.length()) {
                error("Expected JSON value");
            }
            char ch = source.charAt(index);
            switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true");
                case 'f' -> parseLiteral("false");
                case 'n' -> parseLiteral("null");
                default -> {
                    if (ch == '-' || Character.isDigit(ch)) {
                        parseNumber();
                    } else {
                        error("Expected JSON value");
                    }
                }
            }
        }

        private void parseObject() {
            builder.append('{');
            index++;
            skipWhitespace();
            if (consume('}')) {
                builder.append('}');
                return;
            }

            indent++;
            appendNewLine();
            while (true) {
                skipWhitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    error("Expected object key");
                }
                parseString();
                skipWhitespace();
                expect(':', "Expected ':' after object key");
                builder.append(": ");
                parseValue();
                skipWhitespace();
                if (consume(',')) {
                    builder.append(',');
                    appendNewLine();
                    continue;
                }
                if (consume('}')) {
                    indent--;
                    appendNewLine();
                    builder.append('}');
                    return;
                }
                error("Expected ',' or '}'");
            }
        }

        private void parseArray() {
            builder.append('[');
            index++;
            skipWhitespace();
            if (consume(']')) {
                builder.append(']');
                return;
            }

            indent++;
            appendNewLine();
            while (true) {
                parseValue();
                skipWhitespace();
                if (consume(',')) {
                    builder.append(',');
                    appendNewLine();
                    continue;
                }
                if (consume(']')) {
                    indent--;
                    appendNewLine();
                    builder.append(']');
                    return;
                }
                error("Expected ',' or ']'");
            }
        }

        private void parseString() {
            builder.append('"');
            index++;
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') {
                    builder.append(ch);
                    return;
                }
                if (ch == '\\') {
                    parseEscape();
                    continue;
                }
                if (ch < 0x20) {
                    error("Control character is not allowed in string");
                }
                builder.append(ch);
            }
            error("Unterminated string");
        }

        private void parseEscape() {
            if (index >= source.length()) {
                error("Unterminated escape sequence");
            }
            char escape = source.charAt(index++);
            builder.append('\\').append(escape);
            switch (escape) {
                case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                }
                case 'u' -> {
                    for (int i = 0; i < 4; i++) {
                        if (index >= source.length() || !isHex(source.charAt(index))) {
                            error("Invalid unicode escape sequence");
                        }
                        builder.append(source.charAt(index++));
                    }
                }
                default -> error("Invalid escape sequence");
            }
        }

        private void parseNumber() {
            int start = index;
            if (consume('-') && index >= source.length()) {
                error("Invalid number");
            }
            if (consume('0')) {
                if (index < source.length() && Character.isDigit(source.charAt(index))) {
                    error("Leading zero is not allowed in number");
                }
            } else if (index < source.length() && isDigitOneToNine(source.charAt(index))) {
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            } else {
                error("Invalid number");
            }

            if (consume('.')) {
                if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                    error("Expected digit after decimal point");
                }
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }

            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    index++;
                }
                if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                    error("Expected digit in exponent");
                }
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            builder.append(source, start, index);
        }

        private void parseLiteral(String literal) {
            if (!source.startsWith(literal, index)) {
                error("Expected '" + literal + "'");
            }
            builder.append(literal);
            index += literal.length();
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected, String message) {
            if (!consume(expected)) {
                error(message);
            }
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private void appendNewLine() {
            builder.append('\n');
            builder.append("  ".repeat(Math.max(indent, 0)));
        }

        private void error(String message) {
            throw new JsonParseException(message + " at position " + index + ".");
        }

        private static boolean isHex(char ch) {
            return (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
        }

        private static boolean isDigitOneToNine(char ch) {
            return ch >= '1' && ch <= '9';
        }
    }

    private static final class JsonParseException extends RuntimeException {
        private JsonParseException(String message) {
            super(message);
        }
    }
}
