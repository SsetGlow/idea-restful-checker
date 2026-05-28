package com.ssetglow.restfulchecker.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.ssetglow.restfulchecker.config.ProjectConfigResolver;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import com.ssetglow.restfulchecker.settings.RestCheckerGlobalSettings;
import com.ssetglow.restfulchecker.settings.RestCheckerSettings;
import com.ssetglow.restfulchecker.util.JsonBodyFormatter;
import com.ssetglow.restfulchecker.util.KeyValueParser;
import com.ssetglow.restfulchecker.util.PathUtil;
import com.ssetglow.restfulchecker.util.PlaceholderResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CallEndpointDialog extends DialogWrapper {
    private final Project project;
    private final RestEndpoint endpoint;
    private final RestCheckerSettings settings;
    private final RestCheckerGlobalSettings globalSettings;
    private final String endpointKey;
    private final RestCheckerSettings.EndpointRequestData savedRequest;
    private JComboBox<String> hostCombo;
    private JBTextField urlPreview;
    private KeyValueTable variablesTable;
    private KeyValueTable pathVariablesTable;
    private KeyValueTable requestParamsTable;
    private KeyValueTable headersTable;
    private JBTextArea bodyArea;
    private JLabel bodyValidationLabel;
    private JBTextArea responseView;
    private JButton copyResponseBodyButton;
    private JLabel copyResponseBodyStatus;
    private Timer copyResponseBodyStatusTimer;
    private String responseBody = "";

    public CallEndpointDialog(Project project, RestEndpoint endpoint) {
        super(project, true);
        this.project = project;
        this.endpoint = endpoint;
        this.settings = RestCheckerSettings.getInstance(project);
        this.globalSettings = RestCheckerGlobalSettings.getInstance();
        this.endpointKey = endpointKey(endpoint);
        this.savedRequest = settings.getEndpointRequest(endpointKey);
        setTitle("Call REST Endpoint");
        init();
        setOKButtonText("Send");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        hostCombo = new JComboBox<>(settings.getHosts().toArray(new String[0]));
        hostCombo.setEditable(true);
        hostCombo.setSelectedItem(initialHostTemplate());
        hostCombo.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                updatePreview();
            }
        });

        urlPreview = new JBTextField();
        urlPreview.setEditable(false);
        Map<String, String> initialVariables = initialVariables();
        if (!initialVariables.isEmpty()) {
            variablesTable = new KeyValueTable("Key", "Value", initialVariables, 3);
        }
        pathVariablesTable = new KeyValueTable("Key", "Value", initialPathVariables(), 3);
        requestParamsTable = new KeyValueTable("Key", "Value", initialRequestParams(), 3);
        headersTable = new KeyValueTable("Key", "Value", initialHeaders(), 3);
        bodyArea = area(endpoint.normallyHasRequestBody() ? 8 : 4);
        bodyArea.setText(initialBody());
        bodyValidationLabel = new JLabel();
        bodyValidationLabel.setForeground(JBColor.RED);
        bodyValidationLabel.setVisible(false);
        installBodyJsonSupport();
        responseView = area(9);
        responseView.setEditable(false);
        responseView.setFocusable(true);
        responseView.setLineWrap(false);
        responseView.setText(initialResponseText());
        responseBody = initialResponseBody();
        copyResponseBodyButton = createCopyResponseBodyButton();
        copyResponseBodyStatus = createCopyResponseBodyStatus();

        DocumentListener previewListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updatePreview();
            }
        };
        Runnable tablePreviewListener = this::updatePreview;
        if (variablesTable != null) {
            variablesTable.addChangeListener(tablePreviewListener);
        }
        pathVariablesTable.addChangeListener(tablePreviewListener);
        requestParamsTable.addChangeListener(tablePreviewListener);
        headersTable.addChangeListener(this::updateBodyValidation);
        Component editorComponent = hostCombo.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextComponent textComponent) {
            textComponent.getDocument().addDocumentListener(previewListener);
        }

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(form, row++, "Method", new JLabel(endpoint.getHttpMethod()));
        addRow(form, row++, "Path", new JLabel(endpoint.getPath()));
        addRow(form, row++, "Source", new JLabel(endpoint.getQualifiedMethodName()));
        addRow(form, row++, "Host", hostCombo);
        if (variablesTable != null) {
            addRow(form, row++, "Variables", variablesTable);
        }
        addRow(form, row++, "URL", urlPreview);
        addRow(form, row++, "Path variables", pathVariablesTable);
        addRow(form, row++, "Request params", requestParamsTable);
        addRow(form, row++, "Headers", headersTable);
        addBodyRow(form, row++, "Body", bodyArea, bodyValidationLabel);

        JPanel responsePanel = new JPanel(new BorderLayout(0, 4));
        responsePanel.add(createResponseHeader(), BorderLayout.NORTH);
        responsePanel.add(new JBScrollPane(responseView), BorderLayout.CENTER);
        responsePanel.setPreferredSize(new Dimension(820, 180));
        updateCopyResponseBodyButton();

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(responsePanel, BorderLayout.SOUTH);

        JBScrollPane panel = new JBScrollPane(content);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.setPreferredSize(new Dimension(860, 760));
        panel.setMinimumSize(new Dimension(860, 520));
        updatePreview();
        updateBodyValidation();
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return hostCombo;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), new AbstractAction("Copy cURL") {
            @Override
            public void actionPerformed(ActionEvent event) {
                copyCurl();
            }
        }, getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        setOKActionEnabled(false);
        RequestData requestData;
        try {
            commitTableEdits();
            requestData = buildRequestData();
        } catch (RuntimeException exception) {
            showTransientResponse(exception.getMessage());
            setOKActionEnabled(true);
            return;
        }

        settings.rememberHost(requestData.hostTemplate());
        rememberEndpointRequest();
        showTransientResponse("Sending...");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Calling REST endpoint", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ResponseData responseData = execute(requestData);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisposed()) {
                        setResponse(responseData);
                        rememberEndpointRequest();
                        setOKActionEnabled(true);
                    }
                }, ModalityState.any());
            }
        });
    }

    private void copyCurl() {
        try {
            commitTableEdits();
            RequestData requestData = buildRequestData();
            settings.rememberHost(requestData.hostTemplate());
            rememberEndpointRequest();
            String curl = toCurlCommand(requestData);
            CopyPasteManager.getInstance().setContents(new StringSelection(curl));
            showTransientResponse("Copied cURL to clipboard.\n\n" + curl);
        } catch (RuntimeException exception) {
            showTransientResponse(exception.getMessage());
        }
    }

    private void commitTableEdits() {
        if (variablesTable != null) {
            variablesTable.stopEditing();
        }
        pathVariablesTable.stopEditing();
        requestParamsTable.stopEditing();
        headersTable.stopEditing();
    }

    private RequestData buildRequestData() {
        Map<String, String> variables = currentVariables();
        String hostTemplate = currentHostTemplate();
        String host = PlaceholderResolver.resolve(hostTemplate, variables);
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host is empty.");
        }
        String path = PathUtil.replacePathVariables(endpoint.getPath(), resolveMap(pathVariablesTable.toMap(), variables));
        if (path.contains("{") || path.contains("}")) {
            throw new IllegalArgumentException("Some path variables are still unresolved.");
        }
        String url = PathUtil.joinUrl(host, path);
        url = PathUtil.appendQuery(url, resolveMap(requestParamsTable.toMap(), variables));
        String method = "ANY".equals(endpoint.getHttpMethod()) ? "GET" : endpoint.getHttpMethod();
        Map<String, String> headers = resolveMap(headersTable.toMap(), variables);
        validateBodyJsonOrThrow(headers, bodyArea.getText());
        return new RequestData(hostTemplate, method, url, headers, bodyArea.getText());
    }

    private void installBodyJsonSupport() {
        bodyArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateBodyValidation();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateBodyValidation();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateBodyValidation();
            }
        });

        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
                "formatJsonBody");
        bodyArea.getActionMap().put("formatJsonBody", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                formatBodyJson();
            }
        });
    }

    private void formatBodyJson() {
        String body = bodyArea.getText();
        if (body == null || body.isBlank()) {
            clearBodyValidation();
            return;
        }
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson(body);
        if (!result.valid()) {
            showBodyValidation("Invalid JSON: " + result.errorMessage());
            return;
        }
        int caretPosition = bodyArea.getCaretPosition();
        bodyArea.setText(result.formatted());
        bodyArea.setCaretPosition(Math.min(caretPosition, bodyArea.getText().length()));
        clearBodyValidation();
    }

    private void updateBodyValidation() {
        if (bodyValidationLabel == null || bodyArea == null || headersTable == null) {
            return;
        }
        String error = bodyJsonError(headersTable.toMap(), bodyArea.getText());
        if (error == null) {
            clearBodyValidation();
        } else {
            showBodyValidation("Invalid JSON: " + error);
        }
    }

    private void validateBodyJsonOrThrow(Map<String, String> headers, String body) {
        String error = bodyJsonError(headers, body);
        if (error != null) {
            showBodyValidation("Invalid JSON: " + error);
            bodyArea.requestFocusInWindow();
            throw new IllegalArgumentException("Body JSON is invalid: " + error);
        }
    }

    @Nullable
    private static String bodyJsonError(Map<String, String> headers, String body) {
        if (!shouldValidateJsonBody(headers, body)) {
            return null;
        }
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson(body);
        return result.valid() ? null : result.errorMessage();
    }

    private static boolean shouldValidateJsonBody(Map<String, String> headers, String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String contentType = headerValue(headers, "content-type");
        String trimmed = body.trim();
        return (contentType != null && contentType.toLowerCase().contains("json"))
                || trimmed.startsWith("{")
                || trimmed.startsWith("[");
    }

    @Nullable
    private static String headerValue(Map<String, String> headers, String targetKey) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void showBodyValidation(String message) {
        bodyValidationLabel.setText(message);
        bodyValidationLabel.setVisible(true);
        bodyValidationLabel.getParent().revalidate();
        bodyValidationLabel.getParent().repaint();
    }

    private void clearBodyValidation() {
        bodyValidationLabel.setText("");
        bodyValidationLabel.setVisible(false);
        if (bodyValidationLabel.getParent() != null) {
            bodyValidationLabel.getParent().revalidate();
            bodyValidationLabel.getParent().repaint();
        }
    }

    private ResponseData execute(RequestData requestData) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestData.url()))
                    .timeout(Duration.ofSeconds(60));
            for (Map.Entry<String, String> header : requestData.headers().entrySet()) {
                if (!header.getKey().isBlank()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
            if ("GET".equals(requestData.method())) {
                builder.GET();
            } else if ("DELETE".equals(requestData.method()) && requestData.body().isBlank()) {
                builder.DELETE();
            } else {
                builder.method(requestData.method(), HttpRequest.BodyPublishers.ofString(requestData.body(), StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return formatResponse(requestData, response);
        } catch (Exception exception) {
            return new ResponseData(
                    requestData.method() + " " + requestData.url() + "\n\n" + exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    "");
        }
    }

    private static ResponseData formatResponse(RequestData requestData, HttpResponse<String> response) {
        StringBuilder builder = new StringBuilder();
        builder.append(requestData.method()).append(' ').append(requestData.url()).append("\n\n");
        builder.append("Status: ").append(response.statusCode()).append("\n");
        response.headers().map().forEach((key, values) -> builder
                .append(key)
                .append(": ")
                .append(String.join(", ", values))
                .append('\n'));
        String contentType = response.headers().firstValue("content-type").orElse("");
        String responseBody = JsonBodyFormatter.formatIfJson(contentType, response.body());
        builder.append('\n').append(responseBody);
        return new ResponseData(builder.toString(), responseBody);
    }

    private JPanel createResponseHeader() {
        JPanel header = new JPanel(new BorderLayout());
        JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        copyPanel.add(copyResponseBodyStatus);
        copyPanel.add(copyResponseBodyButton);
        header.add(new JLabel("Response"), BorderLayout.WEST);
        header.add(copyPanel, BorderLayout.EAST);
        return header;
    }

    private JButton createCopyResponseBodyButton() {
        JButton button = new JButton(IconLoader.getIcon("/icons/copyResponseBody.svg", CallEndpointDialog.class));
        button.setToolTipText("Copy response body");
        button.setFocusable(false);
        button.addActionListener(event -> copyResponseBody());
        return button;
    }

    private JLabel createCopyResponseBodyStatus() {
        JLabel label = new JLabel("Copied");
        label.setForeground(JBColor.GRAY);
        label.setVisible(false);
        return label;
    }

    private void copyResponseBody() {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(responseBody));
        showCopyResponseBodyFeedback();
    }

    private void showCopyResponseBodyFeedback() {
        copyResponseBodyStatus.setVisible(true);
        copyResponseBodyStatus.revalidate();
        copyResponseBodyStatus.repaint();
        if (copyResponseBodyStatusTimer != null && copyResponseBodyStatusTimer.isRunning()) {
            copyResponseBodyStatusTimer.stop();
        }
        copyResponseBodyStatusTimer = new Timer(1200, event -> {
            copyResponseBodyStatus.setVisible(false);
            copyResponseBodyStatus.revalidate();
            copyResponseBodyStatus.repaint();
        });
        copyResponseBodyStatusTimer.setRepeats(false);
        copyResponseBodyStatusTimer.start();
    }

    private void setResponse(ResponseData responseData) {
        responseView.setText(responseData.text());
        responseView.setCaretPosition(0);
        responseBody = responseData.body();
        updateCopyResponseBodyButton();
    }

    private void showTransientResponse(String responseText) {
        responseView.setText(responseText == null ? "" : responseText);
        responseView.setCaretPosition(0);
        responseBody = "";
        updateCopyResponseBodyButton();
    }

    private void updateCopyResponseBodyButton() {
        if (copyResponseBodyButton != null) {
            copyResponseBodyButton.setEnabled(responseBody != null && !responseBody.isBlank());
        }
    }

    private void updatePreview() {
        if (urlPreview == null) {
            return;
        }
        try {
            urlPreview.setText(buildRequestData().url());
        } catch (RuntimeException exception) {
            urlPreview.setText(exception.getMessage());
        }
    }

    private String currentHostTemplate() {
        Object selected = hostCombo.getEditor() == null ? hostCombo.getSelectedItem() : hostCombo.getEditor().getItem();
        return selected == null ? "" : selected.toString().trim();
    }

    private String initialHostTemplate() {
        if (savedRequest != null && savedRequest.hostTemplate != null && !savedRequest.hostTemplate.isBlank()) {
            return savedRequest.hostTemplate;
        }
        return settings.getActiveHost();
    }

    private Map<String, String> initialVariables() {
        Map<String, String> variables = defaultEditableVariables();
        if (savedRequest != null && savedRequest.variables != null) {
            variables.putAll(savedRequest.variables);
        }
        return variables;
    }

    private Map<String, String> initialPathVariables() {
        Map<String, String> values = defaultPathVariables();
        if (savedRequest != null && savedRequest.pathVariables != null) {
            values.putAll(savedRequest.pathVariables);
        }
        return values;
    }

    private Map<String, String> initialRequestParams() {
        if (savedRequest == null || savedRequest.requestParams == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(savedRequest.requestParams);
    }

    private Map<String, String> initialHeaders() {
        Map<String, String> headers = defaultHeaders();
        if (savedRequest != null && savedRequest.headers != null) {
            headers.putAll(savedRequest.headers);
        }
        return headers;
    }

    private String initialBody() {
        if (savedRequest == null || savedRequest.body == null) {
            return "";
        }
        return savedRequest.body;
    }

    private String initialResponseText() {
        if (savedRequest == null || savedRequest.responseText == null) {
            return "";
        }
        return savedRequest.responseText;
    }

    private String initialResponseBody() {
        if (savedRequest == null || savedRequest.responseBody == null) {
            return "";
        }
        return savedRequest.responseBody;
    }

    private void rememberEndpointRequest() {
        RestCheckerSettings.EndpointRequestData data = new RestCheckerSettings.EndpointRequestData();
        data.hostTemplate = currentHostTemplate();
        if (variablesTable != null) {
            data.variables = changedValues(variablesTable.toMap(), defaultEditableVariables());
        }
        data.pathVariables = changedValues(pathVariablesTable.toMap(), defaultPathVariables());
        data.requestParams = requestParamsTable.toMap();
        data.headers = changedValues(headersTable.toMap(), defaultHeaders());
        data.body = bodyArea.getText();
        data.responseText = responseView.getText();
        data.responseBody = responseBody;
        settings.rememberEndpointRequest(endpointKey, data);
    }

    private static String endpointKey(RestEndpoint endpoint) {
        return endpoint.getHttpMethod() + " " + endpoint.getPath() + " " + endpoint.getQualifiedMethodName();
    }

    private Map<String, String> currentVariables() {
        Map<String, String> variables = new LinkedHashMap<>(ProjectConfigResolver.resolve(project));
        if (variablesTable != null) {
            variables.putAll(variablesTable.toMap());
        } else {
            variables.putAll(defaultEditableVariables());
        }
        return variables;
    }

    private Map<String, String> defaultVariables() {
        Map<String, String> variables = new LinkedHashMap<>(ProjectConfigResolver.resolve(project));
        variables.putAll(defaultEditableVariables());
        return variables;
    }

    private Map<String, String> defaultEditableVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.putAll(globalSettings.getVariables());
        variables.putAll(settings.getVariables());
        return variables;
    }

    private Map<String, String> defaultPathVariables() {
        List<String> variables = PathUtil.extractPathVariables(endpoint.getPath());
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> configuredVariables = defaultVariables();
        for (String variable : variables) {
            values.put(variable, configuredVariables.getOrDefault(variable, ""));
        }
        return values;
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(KeyValueParser.parseLines(globalSettings.getDefaultHeaders()));
        headers.putAll(KeyValueParser.parseLines(settings.getDefaultHeaders()));
        return headers;
    }

    private static String toCurlCommand(RequestData requestData) {
        StringBuilder builder = new StringBuilder("curl");
        builder.append(" -X ").append(requestData.method());
        builder.append(' ').append(shellQuote(requestData.url()));
        for (Map.Entry<String, String> header : requestData.headers().entrySet()) {
            if (!header.getKey().isBlank()) {
                builder.append(" -H ").append(shellQuote(header.getKey() + ": " + header.getValue()));
            }
        }
        if (!"GET".equals(requestData.method()) && !requestData.body().isBlank()) {
            builder.append(" --data-raw ").append(shellQuote(requestData.body()));
        }
        return builder.toString();
    }

    private static String shellQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\"'\"'") + "'";
    }

    private static Map<String, String> resolveMap(Map<String, String> values, Map<String, String> variables) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            resolved.put(entry.getKey(), PlaceholderResolver.resolve(entry.getValue(), variables));
        }
        return resolved;
    }

    private static Map<String, String> changedValues(Map<String, String> current, Map<String, String> defaults) {
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            if (!defaults.containsKey(entry.getKey()) || !Objects.equals(defaults.get(entry.getKey()), entry.getValue())) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return changed;
    }

    private static JBTextArea area(int rows) {
        JBTextArea area = new JBTextArea(rows, 64);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static void addRow(JPanel form, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = labelConstraints(row);
        form.add(new JLabel(label), labelConstraints);

        GridBagConstraints componentConstraints = componentConstraints(row);
        form.add(component, componentConstraints);
    }

    private static void addTextRow(JPanel form, int row, String label, JBTextArea area) {
        GridBagConstraints labelConstraints = labelConstraints(row);
        form.add(new JLabel(label), labelConstraints);

        GridBagConstraints componentConstraints = componentConstraints(row);
        form.add(new JBScrollPane(area), componentConstraints);
    }

    private static void addBodyRow(JPanel form, int row, String label, JBTextArea area, JLabel validationLabel) {
        GridBagConstraints labelConstraints = labelConstraints(row);
        form.add(new JLabel(label), labelConstraints);

        JPanel bodyPanel = new JPanel(new BorderLayout(0, 4));
        bodyPanel.add(new JBScrollPane(area), BorderLayout.CENTER);
        bodyPanel.add(validationLabel, BorderLayout.SOUTH);

        GridBagConstraints componentConstraints = componentConstraints(row);
        form.add(bodyPanel, componentConstraints);
    }

    private static GridBagConstraints labelConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets.set(4, 0, 4, 12);
        return constraints;
    }

    private static GridBagConstraints componentConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets.set(4, 0, 4, 0);
        return constraints;
    }

    private static final class KeyValueTable extends JPanel {
        private static final int MIN_FIELD_HEIGHT = 30;
        private static final int ROW_GAP = 6;
        private static final int TABLE_WIDTH = 640;

        private final JPanel rowsPanel = new JPanel(new GridBagLayout());
        private final JBScrollPane rowsScrollPane = new JBScrollPane(rowsPanel);
        private final List<Row> rows = new ArrayList<>();
        private final List<Runnable> listeners = new ArrayList<>();
        private final int visibleRows;
        private int selectedRow = -1;

        private KeyValueTable(String keyTitle, String valueTitle, Map<String, String> values, int visibleRows) {
            super(new BorderLayout(0, 4));
            this.visibleRows = Math.max(1, visibleRows);
            add(createHeader(keyTitle, valueTitle), BorderLayout.NORTH);
            rowsScrollPane.setBorder(BorderFactory.createEmptyBorder());
            rowsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            rowsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            rowsScrollPane.getViewport().setOpaque(false);
            rowsScrollPane.setOpaque(false);
            rowsScrollPane.getVerticalScrollBar().setUnitIncrement(rowStride());
            add(rowsScrollPane, BorderLayout.CENTER);
            add(createButtonPanel(), BorderLayout.SOUTH);
            setRows(values);
        }

        private void addChangeListener(Runnable listener) {
            listeners.add(listener);
        }

        private Map<String, String> toMap() {
            Map<String, String> values = new LinkedHashMap<>();
            for (Row row : rows) {
                String key = row.keyField().getText().trim();
                if (!key.isEmpty()) {
                    values.put(key, row.valueField().getText().trim());
                }
            }
            return values;
        }

        private void setRows(Map<String, String> values) {
            rows.clear();
            if (values != null) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    addRow(entry.getKey(), entry.getValue(), false);
                }
            }
            rebuildRows();
        }

        private JPanel createHeader(String keyTitle, String valueTitle) {
            JPanel header = new JPanel(new GridBagLayout());
            GridBagConstraints keyConstraints = rowConstraints(0, 0, 0.35);
            header.add(new JLabel(keyTitle), keyConstraints);

            GridBagConstraints valueConstraints = rowConstraints(0, 1, 0.65);
            header.add(new JLabel(valueTitle), valueConstraints);
            return header;
        }

        private JPanel createButtonPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            JButton addButton = new JButton("Add");
            addButton.addActionListener(event -> {
                addRow("", "", true);
                fireChanged();
            });

            JButton removeButton = new JButton("Remove");
            removeButton.addActionListener(event -> {
                removeSelectedRow();
                fireChanged();
            });
            panel.add(addButton);
            panel.add(removeButton);
            return panel;
        }

        private void addRow(String key, String value, boolean focus) {
            Row row = new Row(textField(), textField());
            row.keyField().setText(key == null ? "" : key);
            row.valueField().setText(value == null ? "" : value);
            addFieldListeners(row);
            rows.add(row);
            rebuildRows();
            if (focus) {
                focusRow(rows.size() - 1);
            }
        }

        private void addFieldListeners(Row row) {
            DocumentListener listener = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    fireChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    fireChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    fireChanged();
                }
            };
            row.keyField().getDocument().addDocumentListener(listener);
            row.valueField().getDocument().addDocumentListener(listener);

            FocusAdapter focusListener = new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    selectedRow = rows.indexOf(row);
                }
            };
            row.keyField().addFocusListener(focusListener);
            row.valueField().addFocusListener(focusListener);
        }

        private void removeSelectedRow() {
            if (rows.isEmpty()) {
                return;
            }
            int row = selectedRow >= 0 && selectedRow < rows.size() ? selectedRow : rows.size() - 1;
            rows.remove(row);
            selectedRow = Math.min(row, rows.size() - 1);
            rebuildRows();
            if (selectedRow >= 0) {
                focusRow(selectedRow);
            }
        }

        private void rebuildRows() {
            rowsPanel.removeAll();
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                rowsPanel.add(row.keyField(), rowConstraints(index, 0, 0.35));
                rowsPanel.add(row.valueField(), rowConstraints(index, 1, 0.65));
            }
            int visibleContentRows = Math.min(visibleRows, rows.size());
            int viewportHeight = rowStride() * visibleContentRows;
            int contentRows = rows.size();
            rowsPanel.setPreferredSize(new Dimension(TABLE_WIDTH, rowStride() * contentRows));
            rowsPanel.setMinimumSize(new Dimension(1, 0));
            rowsScrollPane.setPreferredSize(new Dimension(TABLE_WIDTH, viewportHeight));
            rowsScrollPane.setMinimumSize(new Dimension(1, 0));
            rowsPanel.revalidate();
            rowsPanel.repaint();
            rowsScrollPane.revalidate();
            rowsScrollPane.repaint();
            revalidate();
            repaint();
            resizeDialog();
        }

        private void resizeDialog() {
            SwingUtilities.invokeLater(() -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) {
                    window.pack();
                }
            });
        }

        private static GridBagConstraints rowConstraints(int row, int column, double weightx) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = column;
            constraints.gridy = row;
            constraints.weightx = weightx;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets.set(0, column == 0 ? 0 : 6, ROW_GAP, 0);
            return constraints;
        }

        private static JBTextField textField() {
            JBTextField field = new JBTextField();
            int height = fieldHeight(field);
            Dimension preferredSize = field.getPreferredSize();
            field.setPreferredSize(new Dimension(preferredSize.width, height));
            field.setMinimumSize(new Dimension(1, height));
            return field;
        }

        private static int fieldHeight(JBTextField field) {
            return Math.max(MIN_FIELD_HEIGHT, field.getPreferredSize().height);
        }

        private static int rowStride() {
            return fieldHeight(new JBTextField()) + ROW_GAP;
        }

        private void focusRow(int row) {
            SwingUtilities.invokeLater(() -> {
                if (row < 0 || row >= rows.size()) {
                    return;
                }
                Row target = rows.get(row);
                selectedRow = row;
                rowsPanel.scrollRectToVisible(target.keyField().getBounds());
                target.keyField().requestFocusInWindow();
                target.keyField().selectAll();
            });
        }

        private void stopEditing() {
        }

        private void fireChanged() {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }

        private record Row(JBTextField keyField, JBTextField valueField) {
        }
    }

    private record RequestData(String hostTemplate, String method, String url, Map<String, String> headers, String body) {
    }

    private record ResponseData(String text, String body) {
    }
}
