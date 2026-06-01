package com.ssetglow.restfulchecker.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.ssetglow.restfulchecker.config.ProjectConfigResolver;
import com.ssetglow.restfulchecker.endpoint.EndpointRequestDefaults;
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
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
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
    private static final int REQUEST_BODY_ROWS = 8;
    private static final int OPTIONAL_BODY_ROWS = 4;
    private static final int SPLIT_PANE_DRAG_SIZE = 5;
    private static final int CURL_SPLIT_PANE_DRAG_SIZE = 10;
    private static final int BODY_RESIZE_DRAG_SIZE = 10;

    private final Project project;
    private final RestEndpoint endpoint;
    private final RestCheckerSettings settings;
    private final RestCheckerGlobalSettings globalSettings;
    private final String endpointKey;
    private final RestCheckerSettings.EndpointRequestData savedRequest;
    private final EndpointRequestDefaults.RequestDefaults inferredRequestDefaults;
    private JComboBox<String> hostCombo;
    private JBTextField urlPreview;
    private KeyValueTable variablesTable;
    private KeyValueTable pathVariablesTable;
    private KeyValueTable requestParamsTable;
    private KeyValueTable headersTable;
    private JBTextArea bodyArea;
    private JBTextArea curlArea;
    private JLabel bodyValidationLabel;
    private JBTextArea responseHeaderView;
    private JBTextArea responseBodyView;
    private String responseBody = "";

    public CallEndpointDialog(Project project, RestEndpoint endpoint) {
        super(project, false, DialogWrapper.IdeModalityType.MODELESS);
        this.project = project;
        this.endpoint = endpoint;
        this.settings = RestCheckerSettings.getInstance(project);
        this.globalSettings = RestCheckerGlobalSettings.getInstance();
        this.endpointKey = endpointKey(endpoint);
        this.savedRequest = settings.getEndpointRequest(endpointKey);
        this.inferredRequestDefaults = savedRequest == null ? EndpointRequestDefaults.forEndpoint(project, endpoint) : null;
        setTitle("Call REST Endpoint");
        init();
        setOKButtonText("Send");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        hostCombo = new JComboBox<>(globalSettings.getHosts().toArray(new String[0]));
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
        bodyArea = area(endpoint.normallyHasRequestBody() ? REQUEST_BODY_ROWS : OPTIONAL_BODY_ROWS);
        bodyArea.setText(initialBody());
        curlArea = area(8);
        curlArea.setEditable(false);
        curlArea.setFocusable(true);
        curlArea.setLineWrap(true);
        bodyValidationLabel = new JLabel();
        bodyValidationLabel.setForeground(JBColor.RED);
        bodyValidationLabel.setText(" ");
        installBodyJsonSupport();
        responseHeaderView = area(9);
        responseHeaderView.setEditable(false);
        responseHeaderView.setFocusable(true);
        responseHeaderView.setLineWrap(false);
        responseHeaderView.setText(initialResponseHeaderText());
        responseBodyView = area(9);
        responseBodyView.setEditable(false);
        responseBodyView.setFocusable(true);
        responseBodyView.setLineWrap(false);
        responseBodyView.setText(initialResponseBody());
        responseBody = initialResponseBody();

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
        headersTable.addChangeListener(() -> {
            updateBodyValidation();
            updatePreview();
        });
        Component editorComponent = hostCombo.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextComponent textComponent) {
            textComponent.getDocument().addDocumentListener(previewListener);
        }

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(form, row++, "Method", new JLabel(endpoint.getHttpMethod()));
        addRow(form, row++, "Path", new JLabel(endpoint.getPath()));
        addRow(form, row++, "Source", new JLabel(endpoint.getQualifiedMethodName()));
        addRow(form, row++, "Scan prefix", new JLabel(scannedPrefixText()));
        addRow(form, row++, "Host", hostCombo);
        if (variablesTable != null) {
            addRow(form, row++, "Variables", variablesTable);
        }
        addRow(form, row++, "URL", urlPreview);
        addRow(form, row++, "Request", createRequestTabs());
        addVerticalFiller(form, row);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.add(form, BorderLayout.NORTH);

        JBScrollPane requestScrollPane = new JBScrollPane(requestPanel);
        requestScrollPane.setBorder(BorderFactory.createEmptyBorder());
        requestScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        requestScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        requestScrollPane.setMinimumSize(new Dimension(1, JBUI.scale(180)));

        JSplitPane leftPanel = createLeftPanel(requestScrollPane);
        JPanel responsePanel = createResponsePanel();

        JSplitPane panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, responsePanel);
        styleSplitPane(panel);
        panel.setContinuousLayout(true);
        panel.setResizeWeight(0.64);
        panel.setDividerLocation(JBUI.scale(760));
        panel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> pinScrollPaneToTop(requestScrollPane));
        panel.setPreferredSize(new Dimension(1180, 760));
        panel.setMinimumSize(new Dimension(960, 560));
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
        return new Action[]{getOKAction(), getCancelAction()};
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

        globalSettings.rememberHost(requestData.hostTemplate());
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
        String pathWithQuery = PathUtil.appendQuery(path, resolveMap(requestParamsTable.toMap(), variables));
        String url = PathUtil.joinUrl(host, pathWithQuery);
        String method = "ANY".equals(endpoint.getHttpMethod()) ? "GET" : endpoint.getHttpMethod();
        Map<String, String> headers = resolveMap(headersTable.toMap(), variables);
        validateBodyJsonOrThrow(headers, bodyArea.getText());
        return new RequestData(hostTemplate, method, url, pathWithQuery, headers, bodyArea.getText());
    }

    private void installBodyJsonSupport() {
        bodyArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateBodyValidation();
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateBodyValidation();
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateBodyValidation();
                updatePreview();
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
        formatBodyJsonIfPossible();
    }

    private void formatBodyJsonIfPossible() {
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

    private void formatResponseBodyIfPossible() {
        if (responseBodyView == null) {
            return;
        }
        String body = responseBodyView.getText();
        if (body == null || body.isBlank()) {
            return;
        }
        JsonBodyFormatter.JsonFormatResult result = JsonBodyFormatter.formatJson(body);
        if (!result.valid()) {
            return;
        }
        int caretPosition = responseBodyView.getCaretPosition();
        responseBodyView.setText(result.formatted());
        responseBodyView.setCaretPosition(Math.min(caretPosition, responseBodyView.getText().length()));
        responseBody = responseBodyView.getText();
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
        bodyValidationLabel.getParent().revalidate();
        bodyValidationLabel.getParent().repaint();
    }

    private void clearBodyValidation() {
        bodyValidationLabel.setText(" ");
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
        return new ResponseData(builder.toString(), responseBody);
    }

    private JTabbedPane createRequestTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Path variables", pathVariablesTable);
        tabs.addTab("Request params", requestParamsTable);
        tabs.addTab("Headers", headersTable);
        tabs.addTab("Body", new ResizableBodyPanel(
                bodyArea,
                bodyValidationLabel,
                fixedTextAreaHeight(bodyArea, bodyArea.getRows())
        ));
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedComponent() instanceof ResizableBodyPanel) {
                formatBodyJsonIfPossible();
            }
        });
        return tabs;
    }

    private JSplitPane createLeftPanel(JComponent requestScrollPane) {
        JPanel curlPanel = createCurlPanel();
        int savedCurlHeight = settings.getCurlInfoHeight();
        curlPanel.setPreferredSize(new Dimension(1, savedCurlHeight > 0 ? savedCurlHeight : JBUI.scale(300)));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestScrollPane, curlPanel);
        styleSplitPane(splitPane, CURL_SPLIT_PANE_DRAG_SIZE, true);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.6);
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> rememberCurlInfoHeight(splitPane));
        SwingUtilities.invokeLater(() -> {
            int height = splitPane.getHeight();
            int curlHeight = settings.getCurlInfoHeight();
            if (height > 0 && curlHeight > 0 && height > curlHeight + splitPane.getDividerSize()) {
                splitPane.setDividerLocation(height - curlHeight - splitPane.getDividerSize());
            } else {
                splitPane.setDividerLocation(0.6);
            }
        });
        return splitPane;
    }

    private JPanel createCurlPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("cUrl 信息"), BorderLayout.WEST);

        JBScrollPane scrollPane = new JBScrollPane(curlArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(1, JBUI.scale(96)));
        return panel;
    }

    private void rememberCurlInfoHeight(JSplitPane splitPane) {
        int height = splitPane.getHeight();
        if (height <= 0) {
            return;
        }
        int curlHeight = height - splitPane.getDividerLocation() - splitPane.getDividerSize();
        if (curlHeight > 0) {
            settings.setCurlInfoHeight(curlHeight);
        }
    }

    private JPanel createResponsePanel() {
        JPanel responsePanel = new JPanel(new BorderLayout(0, 4));
        JTabbedPane responseTabs = new JTabbedPane();
        responseTabs.addTab("Header", responseScrollPane(responseHeaderView));
        responseTabs.addTab("Body", responseScrollPane(responseBodyView));
        responseTabs.addChangeListener(event -> {
            if (responseTabs.getSelectedIndex() == 1) {
                formatResponseBodyIfPossible();
            }
        });
        responsePanel.add(createResponseHeader(), BorderLayout.NORTH);
        responsePanel.add(responseTabs, BorderLayout.CENTER);
        responsePanel.setPreferredSize(new Dimension(420, 720));
        responsePanel.setMinimumSize(new Dimension(340, 320));
        return responsePanel;
    }

    private static JBScrollPane responseScrollPane(JBTextArea textArea) {
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    private static void styleSplitPane(JSplitPane splitPane) {
        styleSplitPane(splitPane, SPLIT_PANE_DRAG_SIZE, false);
    }

    private static void styleSplitPane(JSplitPane splitPane, int dividerSize, boolean paintHandle) {
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(JBUI.scale(dividerSize));
        splitPane.setOpaque(false);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    {
                        setBorder(BorderFactory.createEmptyBorder());
                        int cursor = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT
                                ? Cursor.S_RESIZE_CURSOR
                                : Cursor.E_RESIZE_CURSOR;
                        setCursor(Cursor.getPredefinedCursor(cursor));
                    }

                    @Override
                    public void paint(Graphics graphics) {
                        java.awt.Color background = getParent() == null ? getBackground() : getParent().getBackground();
                        if (background == null) {
                            background = javax.swing.UIManager.getColor("Panel.background");
                        }
                        graphics.setColor(background == null ? JBColor.border() : background);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                        if (paintHandle) {
                            paintSplitPaneHandle(graphics, splitPane.getOrientation(), getWidth(), getHeight());
                        }
                    }
                };
            }
        });
    }

    private static void paintSplitPaneHandle(Graphics graphics, int orientation, int width, int height) {
        graphics.setColor(JBColor.border());
        if (orientation == JSplitPane.VERTICAL_SPLIT) {
            int centerY = height / 2;
            int centerX = width / 2;
            int lineWidth = Math.min(JBUI.scale(64), Math.max(0, width - JBUI.scale(24)));
            graphics.drawLine(centerX - lineWidth / 2, centerY, centerX + lineWidth / 2, centerY);
        } else {
            int centerX = width / 2;
            int centerY = height / 2;
            int lineHeight = Math.min(JBUI.scale(64), Math.max(0, height - JBUI.scale(24)));
            graphics.drawLine(centerX, centerY - lineHeight / 2, centerX, centerY + lineHeight / 2);
        }
    }

    private JPanel createResponseHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Response"), BorderLayout.WEST);
        return header;
    }

    private void setResponse(ResponseData responseData) {
        responseHeaderView.setText(responseData.headers());
        responseHeaderView.setCaretPosition(0);
        responseBodyView.setText(responseData.body());
        responseBodyView.setCaretPosition(0);
        responseBody = responseData.body();
    }

    private void showTransientResponse(String responseText) {
        responseHeaderView.setText(responseText == null ? "" : responseText);
        responseHeaderView.setCaretPosition(0);
        responseBodyView.setText("");
        responseBody = "";
    }

    private void updatePreview() {
        if (urlPreview == null && curlArea == null) {
            return;
        }
        try {
            RequestData requestData = buildRequestData();
            if (urlPreview != null) {
                urlPreview.setText(requestData.pathPreview());
            }
            if (curlArea != null) {
                curlArea.setText(toCurlCommand(requestData));
                curlArea.setCaretPosition(0);
            }
        } catch (RuntimeException exception) {
            if (urlPreview != null) {
                urlPreview.setText(exception.getMessage());
            }
            if (curlArea != null) {
                curlArea.setText(exception.getMessage());
                curlArea.setCaretPosition(0);
            }
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
        return globalSettings.getActiveHost();
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
        if (savedRequest != null && savedRequest.requestParams != null) {
            return new LinkedHashMap<>(savedRequest.requestParams);
        }
        return inferredRequestDefaults == null ? Map.of() : new LinkedHashMap<>(inferredRequestDefaults.requestParams());
    }

    private Map<String, String> initialHeaders() {
        Map<String, String> headers = defaultHeaders();
        if (savedRequest != null && savedRequest.headers != null) {
            headers.putAll(savedRequest.headers);
        }
        return headers;
    }

    private String initialBody() {
        if (savedRequest != null) {
            return savedRequest.body == null ? "" : savedRequest.body;
        }
        return inferredRequestDefaults == null ? "" : inferredRequestDefaults.body();
    }

    private String initialResponseHeaderText() {
        if (savedRequest == null || savedRequest.responseText == null) {
            return "";
        }
        String text = savedRequest.responseText;
        String body = savedRequest.responseBody == null ? "" : savedRequest.responseBody;
        if (!body.isBlank() && text.endsWith(body)) {
            return text.substring(0, text.length() - body.length()).stripTrailing();
        }
        return text;
    }

    private String initialResponseBody() {
        if (savedRequest == null || savedRequest.responseBody == null) {
            return "";
        }
        return JsonBodyFormatter.formatIfJson("", savedRequest.responseBody);
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
        data.responseText = responseHeaderView.getText() + (responseBodyView.getText().isBlank() ? "" : "\n\n" + responseBodyView.getText());
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
        return headers;
    }

    private String scannedPrefixText() {
        String prefix = ProjectConfigResolver.requestPathPrefix(ProjectConfigResolver.resolve(project));
        return "/".equals(prefix) ? "None" : prefix;
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

    private static void addVerticalFiller(JPanel form, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        form.add(new JPanel(), constraints);
    }

    private static void pinScrollPaneToTop(JBScrollPane scrollPane) {
        SwingUtilities.invokeLater(() -> {
            scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
            scrollPane.revalidate();
            scrollPane.repaint();
        });
    }

    private static void addTextRow(JPanel form, int row, String label, JBTextArea area) {
        GridBagConstraints labelConstraints = labelConstraints(row);
        form.add(new JLabel(label), labelConstraints);

        GridBagConstraints componentConstraints = componentConstraints(row);
        form.add(new JBScrollPane(area), componentConstraints);
    }

    private static void addBodyRow(JPanel form, int row, String label, JBTextArea area, JLabel validationLabel, int areaHeight) {
        GridBagConstraints labelConstraints = labelConstraints(row);
        form.add(new JLabel(label), labelConstraints);

        JBScrollPane bodyScrollPane = new JBScrollPane(area);
        bodyScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        setFixedHeight(bodyScrollPane, areaHeight);

        JPanel bodyPanel = new JPanel(new BorderLayout(0, 4));
        bodyPanel.add(bodyScrollPane, BorderLayout.CENTER);
        bodyPanel.add(validationLabel, BorderLayout.SOUTH);

        GridBagConstraints componentConstraints = componentConstraints(row);
        form.add(bodyPanel, componentConstraints);
    }

    private static int fixedTextAreaHeight(JBTextArea area, int rows) {
        FontMetrics metrics = area.getFontMetrics(area.getFont());
        return Math.max(JBUI.scale(96), metrics.getHeight() * rows + JBUI.scale(18));
    }

    private static void setFixedHeight(JComponent component, int height) {
        Dimension preferredSize = component.getPreferredSize();
        component.setPreferredSize(new Dimension(preferredSize.width, height));
        component.setMinimumSize(new Dimension(1, height));
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

    private static final class ResizableBodyPanel extends JPanel {
        private static final int MAX_BODY_HEIGHT = 420;

        private final JSplitPane splitPane;
        private final int initialHeight;

        private ResizableBodyPanel(JBTextArea area, JLabel validationLabel, int initialHeight) {
            super(new BorderLayout());
            this.initialHeight = Math.min(JBUI.scale(MAX_BODY_HEIGHT), initialHeight);

            JBScrollPane scrollPane = new JBScrollPane(area);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setMinimumSize(new Dimension(1, fixedTextAreaHeight(area, 2)));
            scrollPane.setPreferredSize(new Dimension(1, this.initialHeight));

            JPanel validationPanel = new JPanel(new BorderLayout());
            validationPanel.add(validationLabel, BorderLayout.NORTH);
            int validationHeight = Math.max(JBUI.scale(24), validationLabel.getPreferredSize().height);
            validationPanel.setMinimumSize(new Dimension(1, validationHeight));
            validationPanel.setPreferredSize(new Dimension(1, Math.max(validationHeight, JBUI.scale(MAX_BODY_HEIGHT) - this.initialHeight)));

            splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, validationPanel);
            styleSplitPane(splitPane, BODY_RESIZE_DRAG_SIZE, true);
            splitPane.setContinuousLayout(true);
            splitPane.setResizeWeight(0);
            add(splitPane, BorderLayout.CENTER);

            int preferredHeight = JBUI.scale(MAX_BODY_HEIGHT) + JBUI.scale(BODY_RESIZE_DRAG_SIZE) + validationHeight;
            setPreferredSize(new Dimension(1, preferredHeight));
            setMinimumSize(new Dimension(1, this.initialHeight + JBUI.scale(BODY_RESIZE_DRAG_SIZE) + validationHeight));
            SwingUtilities.invokeLater(this::resetDividerLocation);
        }

        private void resetDividerLocation() {
            int height = splitPane.getHeight();
            int bottomHeight = splitPane.getBottomComponent().getMinimumSize().height;
            if (height > 0) {
                splitPane.setDividerLocation(Math.min(initialHeight, height - splitPane.getDividerSize() - bottomHeight));
            } else {
                splitPane.setDividerLocation(initialHeight);
            }
        }
    }

    private static final class KeyValueTable extends JPanel {
        private static final int MIN_FIELD_HEIGHT = 30;
        private static final int ROW_GAP = 6;
        private static final int TABLE_WIDTH = 640;

        private final JPanel headerPanel;
        private final JPanel rowsPanel = new JPanel(new GridBagLayout());
        private final JBScrollPane rowsScrollPane = new JBScrollPane(rowsPanel);
        private final List<Row> rows = new ArrayList<>();
        private final List<Runnable> listeners = new ArrayList<>();
        private final int visibleRows;
        private int selectedRow = -1;

        private KeyValueTable(String keyTitle, String valueTitle, Map<String, String> values, int visibleRows) {
            super(new BorderLayout(0, 2));
            this.visibleRows = Math.max(1, visibleRows);
            headerPanel = createHeader(keyTitle, valueTitle);
            add(headerPanel, BorderLayout.NORTH);
            rowsScrollPane.setBorder(BorderFactory.createEmptyBorder());
            rowsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            rowsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            rowsScrollPane.getViewport().setOpaque(false);
            rowsScrollPane.setOpaque(false);
            rowsScrollPane.getVerticalScrollBar().setUnitIncrement(rowStride());
            add(rowsScrollPane, BorderLayout.CENTER);
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
            if (values != null && !values.isEmpty()) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    addRowAt(rows.size(), entry.getKey(), entry.getValue(), false);
                }
            } else {
                addRowAt(0, "", "", false);
            }
            rebuildRows();
        }

        private JPanel createHeader(String keyTitle, String valueTitle) {
            JPanel header = new JPanel(new GridBagLayout());
            header.add(new JLabel(""), rowConstraints(0, 0, 0));

            GridBagConstraints keyConstraints = rowConstraints(0, 1, 0.35);
            header.add(new JLabel(keyTitle), keyConstraints);

            GridBagConstraints valueConstraints = rowConstraints(0, 2, 0.65);
            header.add(new JLabel(valueTitle), valueConstraints);
            return header;
        }

        private void addRowAt(int index, String key, String value, boolean focus) {
            Row row = new Row(new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)), textField(), textField());
            JButton addButton = rowButton("+");
            JButton removeButton = rowButton("-");
            addButton.addActionListener(event -> {
                int rowIndex = rows.indexOf(row);
                addRowAt(rowIndex < 0 ? rows.size() : rowIndex + 1, "", "", true);
                fireChanged();
            });
            removeButton.addActionListener(event -> {
                removeRow(rows.indexOf(row));
                fireChanged();
            });
            row.controlsPanel().add(addButton);
            row.controlsPanel().add(removeButton);
            row.keyField().setText(key == null ? "" : key);
            row.valueField().setText(value == null ? "" : value);
            addFieldListeners(row);
            rows.add(Math.max(0, Math.min(index, rows.size())), row);
            rebuildRows();
            if (focus) {
                focusRow(rows.indexOf(row));
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

        private void removeRow(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return;
            }
            rows.remove(rowIndex);
            if (rows.isEmpty()) {
                addRowAt(0, "", "", false);
                selectedRow = 0;
                return;
            }
            selectedRow = Math.min(rowIndex, rows.size() - 1);
            rebuildRows();
            if (selectedRow >= 0) {
                focusRow(selectedRow);
            }
        }

        private void rebuildRows() {
            rowsPanel.removeAll();
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                rowsPanel.add(row.controlsPanel(), rowConstraints(index, 0, 0));
                rowsPanel.add(row.keyField(), rowConstraints(index, 1, 0.35));
                rowsPanel.add(row.valueField(), rowConstraints(index, 2, 0.65));
            }
            GridBagConstraints fillerConstraints = new GridBagConstraints();
            fillerConstraints.gridx = 0;
            fillerConstraints.gridy = rows.size();
            fillerConstraints.gridwidth = 3;
            fillerConstraints.weightx = 1;
            fillerConstraints.weighty = 1;
            fillerConstraints.fill = GridBagConstraints.BOTH;
            rowsPanel.add(new JPanel(), fillerConstraints);
            int visibleContentRows = Math.min(visibleRows, rows.size());
            int viewportHeight = rowStride() * visibleContentRows;
            int contentRows = rows.size();
            rowsPanel.setPreferredSize(new Dimension(TABLE_WIDTH, rowStride() * contentRows));
            rowsPanel.setMinimumSize(new Dimension(1, 0));
            rowsScrollPane.setPreferredSize(new Dimension(TABLE_WIDTH, viewportHeight));
            rowsScrollPane.setMinimumSize(new Dimension(1, viewportHeight));
            int tableHeight = headerPanel.getPreferredSize().height
                    + viewportHeight
                    + JBUI.scale(4);
            setPreferredSize(new Dimension(TABLE_WIDTH, tableHeight));
            setMinimumSize(new Dimension(1, tableHeight));
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
            constraints.fill = column == 0 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets.set(0, column == 0 ? 0 : 6, ROW_GAP, 0);
            return constraints;
        }

        private static JButton rowButton(String text) {
            JButton button = new JButton(text);
            button.setFocusable(false);
            button.setMargin(JBUI.insets(0, 3));
            int size = fieldHeight(new JBTextField());
            button.setPreferredSize(new Dimension(JBUI.scale(24), size));
            button.setMinimumSize(new Dimension(JBUI.scale(24), size));
            return button;
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

        private record Row(JPanel controlsPanel, JBTextField keyField, JBTextField valueField) {
        }
    }

    private record RequestData(String hostTemplate, String method, String url, String pathPreview, Map<String, String> headers, String body) {
    }

    private record ResponseData(String headers, String body) {
    }
}
