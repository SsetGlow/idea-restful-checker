package com.ssetglow.restfulchecker.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(name = "RestfulCheckerGlobalSettings", storages = @Storage("restfulChecker.xml"))
public final class RestCheckerGlobalSettings implements PersistentStateComponent<RestCheckerGlobalSettings.StateData> {
    private StateData state = new StateData();

    public static RestCheckerGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(RestCheckerGlobalSettings.class);
    }

    public List<String> getHosts() {
        ensureDefaults();
        return new ArrayList<>(state.hosts);
    }

    public void setHosts(List<String> hosts) {
        state.hosts = new ArrayList<>();
        if (hosts != null) {
            for (String host : hosts) {
                if (host != null && !host.isBlank() && !state.hosts.contains(host.trim())) {
                    state.hosts.add(host.trim());
                }
            }
        }
        ensureDefaults();
    }

    public void rememberHost(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        ensureDefaults();
        String normalized = host.trim();
        state.activeHost = normalized;
        if (!state.hosts.contains(normalized)) {
            state.hosts.add(0, normalized);
        }
    }

    public String getActiveHost() {
        ensureDefaults();
        if (state.activeHost == null || state.activeHost.isBlank()) {
            return state.hosts.get(0);
        }
        return state.activeHost;
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
        if (state.hosts == null) {
            state.hosts = new ArrayList<>();
        }
        if (state.hosts.isEmpty()) {
            state.hosts.add(RestCheckerSettings.DEFAULT_HOST);
        }
        if (state.activeHost == null || state.activeHost.isBlank() || !state.hosts.contains(state.activeHost)) {
            state.activeHost = state.hosts.get(0);
        }
        if (state.variables == null) {
            state.variables = new LinkedHashMap<>();
        }
        if (state.defaultHeaders == null || state.defaultHeaders.isBlank()) {
            state.defaultHeaders = RestCheckerSettings.DEFAULT_HEADERS;
        }
    }

    public static final class StateData {
        public List<String> hosts = new ArrayList<>(List.of(RestCheckerSettings.DEFAULT_HOST));
        public String activeHost = RestCheckerSettings.DEFAULT_HOST;
        public Map<String, String> variables = new LinkedHashMap<>();
        public String defaultHeaders = RestCheckerSettings.DEFAULT_HEADERS;
    }
}
