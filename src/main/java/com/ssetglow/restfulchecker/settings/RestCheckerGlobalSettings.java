package com.ssetglow.restfulchecker.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@State(name = "IdeaRestfulCheckerGlobalSettings", storages = @Storage("ideaRestfulChecker.xml"))
public final class RestCheckerGlobalSettings implements PersistentStateComponent<RestCheckerGlobalSettings.StateData> {
    private StateData state = new StateData();

    public static RestCheckerGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(RestCheckerGlobalSettings.class);
    }

    @Override
    public StateData getState() {
        ensureDefaults();
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
        ensureDefaults();
    }

    public Map<String, String> getVariables() {
        ensureDefaults();
        return new LinkedHashMap<>(state.variables);
    }

    public void setVariables(Map<String, String> variables) {
        state.variables = new LinkedHashMap<>();
        if (variables != null) {
            state.variables.putAll(variables);
        }
    }

    public String getDefaultHeaders() {
        ensureDefaults();
        return state.defaultHeaders;
    }

    public void setDefaultHeaders(String defaultHeaders) {
        state.defaultHeaders = defaultHeaders == null ? "" : defaultHeaders;
    }

    private void ensureDefaults() {
        if (state == null) {
            state = new StateData();
        }
        if (state.variables == null) {
            state.variables = new LinkedHashMap<>();
        }
        if (state.defaultHeaders == null || state.defaultHeaders.isBlank()) {
            state.defaultHeaders = RestCheckerSettings.DEFAULT_HEADERS;
        }
    }

    public static final class StateData {
        public Map<String, String> variables = new LinkedHashMap<>();
        public String defaultHeaders = RestCheckerSettings.DEFAULT_HEADERS;
    }
}
