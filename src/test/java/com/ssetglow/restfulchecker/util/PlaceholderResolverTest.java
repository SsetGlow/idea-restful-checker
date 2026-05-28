package com.ssetglow.restfulchecker.util;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PlaceholderResolverTest {
    @Test
    public void resolvesVariablesAndFallbacks() {
        String value = PlaceholderResolver.resolve(
                "http://localhost:${server.port:8080}/${app.name}",
                Map.of("server.port", "9000", "app.name", "demo")
        );
        assertEquals("http://localhost:9000/demo", value);
    }

    @Test
    public void leavesUnknownVariables() {
        assertEquals("${missing}", PlaceholderResolver.resolve("${missing}", Map.of()));
    }
}
