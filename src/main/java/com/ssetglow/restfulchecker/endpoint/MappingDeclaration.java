package com.ssetglow.restfulchecker.endpoint;

import java.util.List;

final class MappingDeclaration {
    private final List<String> paths;
    private final List<String> methods;

    MappingDeclaration(List<String> paths, List<String> methods) {
        this.paths = paths == null || paths.isEmpty() ? List.of("") : paths;
        this.methods = methods == null || methods.isEmpty() ? List.of("ANY") : methods;
    }

    List<String> paths() {
        return paths;
    }

    List<String> methods() {
        return methods;
    }
}
