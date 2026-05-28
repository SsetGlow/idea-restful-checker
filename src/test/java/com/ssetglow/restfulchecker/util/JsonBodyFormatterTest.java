package com.ssetglow.restfulchecker.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonBodyFormatterTest {
    @Test
    public void formatsJsonObject() {
        assertEquals("{\n"
                        + "  \"code\": 200,\n"
                        + "  \"data\": {\n"
                        + "    \"cities\": []\n"
                        + "  }\n"
                        + "}",
                JsonBodyFormatter.formatIfJson("application/json", "{\"code\":200,\"data\":{\"cities\":[]}}"));
    }

    @Test
    public void keepsNonJsonText() {
        assertEquals("hello", JsonBodyFormatter.formatIfJson("text/plain", "hello"));
    }

    @Test
    public void keepsInvalidJson() {
        assertEquals("{oops", JsonBodyFormatter.formatIfJson("application/json", "{oops"));
    }

    @Test
    public void rejectsInvalidObjectValue() {
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson("{\"name\":}");

        assertFalse(result.valid());
    }

    @Test
    public void rejectsTrailingComma() {
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson("{\"name\":\"codex\",}");

        assertFalse(result.valid());
    }

    @Test
    public void acceptsJsonArray() {
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson("[{\"id\":1},true,null]");

        assertTrue(result.valid());
        assertEquals("[\n"
                        + "  {\n"
                        + "    \"id\": 1\n"
                        + "  },\n"
                        + "  true,\n"
                        + "  null\n"
                        + "]",
                result.formatted());
    }
}
