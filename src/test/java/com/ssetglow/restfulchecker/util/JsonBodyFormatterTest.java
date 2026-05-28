package com.ssetglow.restfulchecker.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
