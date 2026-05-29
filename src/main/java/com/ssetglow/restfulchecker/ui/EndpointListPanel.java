package com.ssetglow.restfulchecker.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class EndpointListPanel extends JPanel {
    private static final int FILTER_DELAY_MILLIS = 90;

    private final boolean showSearch;
    private final boolean activateOnSingleClick;
    private final Consumer<RestEndpoint> activationHandler;
    private final AtomicInteger filterGeneration = new AtomicInteger();
    private List<EndpointRow> endpoints = List.of();
    private DefaultListModel<EndpointRow> model = new DefaultListModel<>();
    private final JBTextField searchField;
    private final JBList<EndpointRow> endpointList;
    private final Timer filterTimer;

    public EndpointListPanel(List<RestEndpoint> endpoints, boolean showSearch, @Nullable Consumer<RestEndpoint> activationHandler) {
        this(endpoints, showSearch, activationHandler, false);
    }

    public EndpointListPanel(
            List<RestEndpoint> endpoints,
            boolean showSearch,
            @Nullable Consumer<RestEndpoint> activationHandler,
            boolean activateOnSingleClick
    ) {
        super(new BorderLayout(0, 8));
        this.showSearch = showSearch;
        this.activateOnSingleClick = activateOnSingleClick;
        this.activationHandler = activationHandler;
        Font editorFont = editorFont();
        searchField = new JBTextField();
        searchField.setFont(editorFont);
        endpointList = new JBList<>(model);
        endpointList.setFont(editorFont);
        endpointList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        endpointList.setFixedCellHeight(rowHeight(editorFont));
        endpointList.setCellRenderer(new EndpointRenderer());
        endpointList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if ((activateOnSingleClick && event.getClickCount() == 1) || event.getClickCount() == 2) {
                    activateSelectedEndpoint();
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleFilter();
            }
        });
        filterTimer = new Timer(FILTER_DELAY_MILLIS, event -> filterAsync());
        filterTimer.setRepeats(false);
        installSearchNavigation();
        installCopyPathAction();
        if (showSearch) {
            add(searchField, BorderLayout.NORTH);
        }
        add(new JBScrollPane(endpointList), BorderLayout.CENTER);
        setEndpoints(endpoints);
    }

    public void setEndpoints(List<RestEndpoint> endpoints) {
        this.endpoints = endpoints == null ? List.of() : endpoints.stream()
                .map(EndpointRow::new)
                .toList();
        applyFilterResult(this.endpoints, null);
    }

    @Nullable
    public RestEndpoint getSelectedEndpoint() {
        EndpointRow row = endpointList.getSelectedValue();
        return row == null ? null : row.endpoint();
    }

    public JComponent getPreferredFocusedComponent() {
        return showSearch ? searchField : endpointList;
    }

    public void requestSearchFocus() {
        if (!showSearch) {
            endpointList.requestFocusInWindow();
            return;
        }
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    public void dispose() {
        filterTimer.stop();
    }

    private static Font editorFont() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        return scheme.getFont(EditorFontType.PLAIN);
    }

    private static int rowHeight(Font font) {
        return Math.max(28, font.getSize() + 10);
    }

    private void installSearchNavigation() {
        bindSearchKey(KeyEvent.VK_DOWN, "selectNextEndpoint", () -> moveSelection(1));
        bindSearchKey(KeyEvent.VK_UP, "selectPreviousEndpoint", () -> moveSelection(-1));
        bindSearchKey(KeyEvent.VK_PAGE_DOWN, "selectNextEndpointPage", () -> moveSelection(Math.max(endpointList.getVisibleRowCount(), 1)));
        bindSearchKey(KeyEvent.VK_PAGE_UP, "selectPreviousEndpointPage", () -> moveSelection(-Math.max(endpointList.getVisibleRowCount(), 1)));
        bindSearchKey(KeyEvent.VK_ENTER, "activateSelectedEndpoint", this::activateSelectedEndpoint);
    }

    private void installCopyPathAction() {
        bindCopyPathKey(searchField, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.META_DOWN_MASK));
        bindCopyPathKey(endpointList, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.META_DOWN_MASK));
        bindCopyPathKey(searchField, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK));
        bindCopyPathKey(endpointList, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK));
    }

    private void bindCopyPathKey(JComponent component, KeyStroke keyStroke) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, "copySelectedEndpointPath");
        component.getActionMap().put("copySelectedEndpointPath", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                copySelectedEndpointPath();
            }
        });
    }

    private void copySelectedEndpointPath() {
        RestEndpoint endpoint = getSelectedEndpoint();
        if (endpoint == null) {
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(endpoint.getPath()));
    }

    private void activateSelectedEndpoint() {
        RestEndpoint endpoint = getSelectedEndpoint();
        if (endpoint != null && activationHandler != null) {
            activationHandler.accept(endpoint);
        }
    }

    private void bindSearchKey(int keyCode, String actionName, Runnable action) {
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
        searchField.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
        endpointList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
        endpointList.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void moveSelection(int delta) {
        if (model.isEmpty()) {
            return;
        }
        int nextIndex = endpointList.getSelectedIndex();
        if (nextIndex < 0) {
            nextIndex = delta < 0 ? model.size() - 1 : 0;
        } else {
            nextIndex = Math.max(0, Math.min(model.size() - 1, nextIndex + delta));
        }
        endpointList.setSelectedIndex(nextIndex);
        endpointList.ensureIndexIsVisible(nextIndex);
    }

    private void scheduleFilter() {
        filterTimer.restart();
    }

    private void filterAsync() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        EndpointRow selectedRow = endpointList.getSelectedValue();
        int generation = filterGeneration.incrementAndGet();
        String[] tokens = query.isBlank() ? new String[0] : query.split("\\s+");
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            List<EndpointRow> filtered = filterRows(tokens);
            DefaultListModel<EndpointRow> nextModel = new DefaultListModel<>();
            for (EndpointRow row : filtered) {
                nextModel.addElement(row);
            }
            SwingUtilities.invokeLater(() -> {
                if (generation == filterGeneration.get()) {
                    applyFilterResult(nextModel, selectedRow);
                }
            });
        });
    }

    private List<EndpointRow> filterRows(String[] tokens) {
        if (tokens.length == 0) {
            return endpoints;
        }
        List<EndpointRow> filtered = new ArrayList<>();
        for (EndpointRow row : endpoints) {
            if (row.matches(tokens)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private void applyFilterResult(List<EndpointRow> rows, @Nullable EndpointRow selectedRow) {
        DefaultListModel<EndpointRow> nextModel = new DefaultListModel<>();
        for (EndpointRow row : rows) {
            nextModel.addElement(row);
        }
        applyFilterResult(nextModel, selectedRow);
    }

    private void applyFilterResult(DefaultListModel<EndpointRow> nextModel, @Nullable EndpointRow selectedRow) {
        model = nextModel;
        endpointList.setModel(model);
        if (model.isEmpty()) {
            return;
        }
        int selectedIndex = selectedRow == null ? -1 : model.indexOf(selectedRow);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        endpointList.setSelectedIndex(selectedIndex);
        endpointList.ensureIndexIsVisible(selectedIndex);
    }

    private record EndpointRow(RestEndpoint endpoint, String searchableText) {
        private EndpointRow(RestEndpoint endpoint) {
            this(endpoint, (endpoint.getHttpMethod() + " " + endpoint.getPath() + " "
                    + endpoint.getQualifiedMethodName() + " " + endpoint.getFilePath()).toLowerCase(Locale.ROOT));
        }

        private boolean matches(String[] tokens) {
            for (String token : tokens) {
                if (!searchableText.contains(token)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class EndpointRenderer extends JComponent implements ListCellRenderer<EndpointRow> {
        private static final Color POST_COLOR = new JBColor(new Color(0x128D45), new Color(0x4FD17F));
        private static final Color GET_COLOR = new JBColor(new Color(0x348C2F), new Color(0xA9DFA0));
        private static final Color PUT_COLOR = new JBColor(new Color(0x96720E), new Color(0xF2D982));
        private static final Color DELETE_COLOR = new JBColor(new Color(0xC93636), new Color(0xFF6E6E));
        private static final Color DEFAULT_METHOD_COLOR = new JBColor(new Color(0x555555), new Color(0xC8C8C8));
        private static final Color PATH_COLOR = new JBColor(new Color(0x1677AD), new Color(0x8ECFFF));
        private static final Color PACKAGE_COLOR = new JBColor(new Color(0xB94B56), new Color(0xFFA1A8));
        private static final Color METHOD_NAME_COLOR = new JBColor(new Color(0x8E6A2C), new Color(0xD8B98A));
        private static final int HORIZONTAL_PADDING = 6;
        private static final int SEGMENT_GAP = 18;

        private EndpointRow row;
        private JList<? extends EndpointRow> list;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends EndpointRow> list,
                EndpointRow value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            this.row = value;
            this.list = list;
            this.selected = isSelected;
            setFont(list.getFont());
            setOpaque(true);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setFont(getFont());
                GraphicsUtil.setupAntialiasing(graphics2D);
                graphics2D.setColor(selected ? list.getSelectionBackground() : list.getBackground());
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
                if (row == null) {
                    return;
                }
                RestEndpoint endpoint = row.endpoint();
                FontMetrics metrics = graphics2D.getFontMetrics(getFont());
                int x = HORIZONTAL_PADDING;
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                x = drawSegment(graphics2D, metrics, x, baseline, methodColor(endpoint.getHttpMethod()), endpoint.getHttpMethod());
                x = drawGap(x);
                x = drawSegment(graphics2D, metrics, x, baseline, PATH_COLOR, endpoint.getPath());
                x = drawGap(x);
                x = drawSegment(graphics2D, metrics, x, baseline, PACKAGE_COLOR, endpoint.getClassName());
                x = drawSegment(graphics2D, metrics, x, baseline, DEFAULT_METHOD_COLOR, "#");
                drawSegment(graphics2D, metrics, x, baseline, METHOD_NAME_COLOR, endpoint.getMethodName());
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (row == null) {
                return new Dimension(200, rowHeight(getFont()));
            }
            FontMetrics metrics = getFontMetrics(getFont());
            RestEndpoint endpoint = row.endpoint();
            int width = HORIZONTAL_PADDING * 2
                    + metrics.stringWidth(endpoint.getHttpMethod())
                    + SEGMENT_GAP
                    + metrics.stringWidth(endpoint.getPath())
                    + SEGMENT_GAP
                    + metrics.stringWidth(endpoint.getClassName())
                    + metrics.stringWidth("#")
                    + metrics.stringWidth(endpoint.getMethodName());
            return new Dimension(width, rowHeight(getFont()));
        }

        private static int drawSegment(Graphics2D graphics, FontMetrics metrics, int x, int baseline, Color color, String text) {
            String safeText = text == null ? "" : text;
            graphics.setColor(color);
            graphics.drawString(safeText, x, baseline);
            return x + metrics.stringWidth(safeText);
        }

        private static int drawGap(int x) {
            return x + SEGMENT_GAP;
        }

        private static Color methodColor(String method) {
            return switch (method) {
                case "POST" -> POST_COLOR;
                case "GET" -> GET_COLOR;
                case "PUT" -> PUT_COLOR;
                case "DELETE" -> DELETE_COLOR;
                default -> DEFAULT_METHOD_COLOR;
            };
        }
    }
}
