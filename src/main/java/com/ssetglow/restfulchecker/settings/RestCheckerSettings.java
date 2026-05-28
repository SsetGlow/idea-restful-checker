package com.ssetglow.restfulchecker.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(name = "IdeaRestfulCheckerSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class RestCheckerSettings implements PersistentStateComponent<RestCheckerSettings.StateData> {
    public static final String DEFAULT_HOST = "http://localhost:${server.port:8080}";
    public static final String DEFAULT_HEADERS = "Accept: application/json\nContent-Type: application/json";

    private StateData state = new StateData();

    public static RestCheckerSettings getInstance(Project project) {
        return project.getService(RestCheckerSettings.class);
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

    public EndpointRequestData getEndpointRequest(String key) {
        ensureDefaults();
        if (key == null || key.isBlank()) {
            return null;
        }
        EndpointRequestData data = state.endpointRequests.get(key);
        return data == null ? null : data.copy();
    }

    public void rememberEndpointRequest(String key, EndpointRequestData data) {
        ensureDefaults();
        if (key == null || key.isBlank() || data == null) {
            return;
        }
        state.endpointRequests.put(key, data.copy());
    }

    private void ensureDefaults() {
        if (state == null) {
            state = new StateData();
        }
        if (state.hosts == null) {
            state.hosts = new ArrayList<>();
        }
        if (state.hosts.isEmpty()) {
            state.hosts.add(DEFAULT_HOST);
        }
        if (state.variables == null) {
            state.variables = new LinkedHashMap<>();
        }
        if (state.defaultHeaders == null || state.defaultHeaders.isBlank()) {
            state.defaultHeaders = DEFAULT_HEADERS;
        }
        if (state.endpointRequests == null) {
            state.endpointRequests = new LinkedHashMap<>();
        }
    }

    public static final class StateData {
        public List<String> hosts = new ArrayList<>(List.of(DEFAULT_HOST));
        public String activeHost = DEFAULT_HOST;
        public Map<String, String> variables = new LinkedHashMap<>();
        public String defaultHeaders = DEFAULT_HEADERS;
        public Map<String, EndpointRequestData> endpointRequests = new LinkedHashMap<>();
    }

    public static final class EndpointRequestData {
        public String hostTemplate = "";
        public Map<String, String> variables = new LinkedHashMap<>();
        public Map<String, String> pathVariables = new LinkedHashMap<>();
        public Map<String, String> requestParams = new LinkedHashMap<>();
        public Map<String, String> headers = new LinkedHashMap<>();
        public String body = "";
        public String responseText = "";
        public String responseBody = "";

        public EndpointRequestData copy() {
            EndpointRequestData copy = new EndpointRequestData();
            copy.hostTemplate = hostTemplate == null ? "" : hostTemplate;
            copy.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
            copy.pathVariables = pathVariables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pathVariables);
            copy.requestParams = requestParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestParams);
            copy.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
            copy.body = body == null ? "" : body;
            copy.responseText = responseText == null ? "" : responseText;
            copy.responseBody = responseBody == null ? "" : responseBody;
            return copy;
        }
    }
}
