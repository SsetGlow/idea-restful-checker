package com.ssetglow.restfulchecker.util;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PathUtilTest {
    @Test
    public void joinsPathParts() {
        assertEquals("/api/users/{id}", PathUtil.joinPath("/api/", "/users/", "{id}"));
        assertEquals("/", PathUtil.joinPath("", "/", null));
    }

    @Test
    public void replacesPathVariables() {
        assertEquals("/users/42", PathUtil.replacePathVariables("/users/{id}", Map.of("id", "42")));
    }
}
