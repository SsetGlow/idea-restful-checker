package com.ssetglow.restfulchecker.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ProjectConfigResolverTest {
    @Test
    public void joinsServletContextAndMvcServletPath() {
        assertEquals("/api/internal", ProjectConfigResolver.requestPathPrefix(Map.of(
                "server.servlet.context-path", "/api",
                "spring.mvc.servlet.path", "/internal"
        )));
    }

    @Test
    public void usesWebFluxBasePathWhenMvcServletPathIsMissing() {
        assertEquals("/gateway", ProjectConfigResolver.requestPathPrefix(Map.of(
                "spring.webflux.base-path", "/gateway"
        )));
    }

    @Test
    public void keepsRootWhenNoPrefixIsConfigured() {
        assertEquals("/", ProjectConfigResolver.requestPathPrefix(Map.of()));
    }
}
