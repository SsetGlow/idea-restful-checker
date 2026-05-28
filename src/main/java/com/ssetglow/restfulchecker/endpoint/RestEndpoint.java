package com.ssetglow.restfulchecker.endpoint;

import java.util.Locale;
import java.util.Objects;

public final class RestEndpoint {
    private final String httpMethod;
    private final String path;
    private final String className;
    private final String methodName;
    private final String filePath;
    private final int textOffset;

    public RestEndpoint(String httpMethod, String path, String className, String methodName, String filePath, int textOffset) {
        this.httpMethod = normalizeMethod(httpMethod);
        this.path = path == null || path.isBlank() ? "/" : path;
        this.className = className == null ? "" : className;
        this.methodName = methodName == null ? "" : methodName;
        this.filePath = filePath == null ? "" : filePath;
        this.textOffset = textOffset;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getTextOffset() {
        return textOffset;
    }

    public String getQualifiedMethodName() {
        if (className.isBlank()) {
            return methodName;
        }
        return className + "#" + methodName;
    }

    public String getDisplayText() {
        return httpMethod + " " + path + "  " + getQualifiedMethodName();
    }

    public boolean normallyHasRequestBody() {
        return Objects.equals(httpMethod, "POST")
                || Objects.equals(httpMethod, "PUT")
                || Objects.equals(httpMethod, "PATCH");
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "ANY";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}
