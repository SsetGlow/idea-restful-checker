package com.ssetglow.restfulchecker.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EndpointSearchDialog extends DialogWrapper {
    private final List<RestEndpoint> endpoints;
    private final DefaultListModel<RestEndpoint> model = new DefaultListModel<>();
    private JBTextField searchField;
    private JBList<RestEndpoint> endpointList;
    private RestEndpoint selectedEndpoint;

    public EndpointSearchDialog(Project project, List<RestEndpoint> endpoints) {
        super(project, true);
        this.endpoints = new ArrayList<>(endpoints);
        setTitle("REST Endpoints");
        init();
        setOKButtonText("Go To");
    }

    @Nullable
    public RestEndpoint getSelectedEndpoint() {
        return selectedEndpoint;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        searchField = new JBTextField();
        endpointList = new JBList<>(model);
        endpointList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        endpointList.setCellRenderer(new EndpointRenderer());
        endpointList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    doOKAction();
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filter();
            }
        });
        installSearchNavigation();

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(new JBScrollPane(endpointList), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(760, 420));
        filter();
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return searchField;
    }

    @Override
    protected void doOKAction() {
        selectedEndpoint = endpointList.getSelectedValue();
        if (selectedEndpoint == null) {
            return;
        }
        super.doOKAction();
    }

    private void installSearchNavigation() {
        bindSearchKey(KeyEvent.VK_DOWN, "selectNextEndpoint", () -> moveSelection(1));
        bindSearchKey(KeyEvent.VK_UP, "selectPreviousEndpoint", () -> moveSelection(-1));
        bindSearchKey(KeyEvent.VK_PAGE_DOWN, "selectNextEndpointPage", () -> moveSelection(Math.max(endpointList.getVisibleRowCount(), 1)));
        bindSearchKey(KeyEvent.VK_PAGE_UP, "selectPreviousEndpointPage", () -> moveSelection(-Math.max(endpointList.getVisibleRowCount(), 1)));
        bindSearchKey(KeyEvent.VK_ENTER, "openSelectedEndpoint", this::doOKAction);
    }

    private void bindSearchKey(int keyCode, String actionName, Runnable action) {
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
        searchField.getActionMap().put(actionName, new AbstractAction() {
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

    private void filter() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (RestEndpoint endpoint : endpoints) {
            if (matches(endpoint, query)) {
                model.addElement(endpoint);
            }
        }
        if (!model.isEmpty()) {
            endpointList.setSelectedIndex(0);
            endpointList.ensureIndexIsVisible(0);
        }
    }

    private static boolean matches(RestEndpoint endpoint, String query) {
        if (query.isBlank()) {
            return true;
        }
        String haystack = (endpoint.getHttpMethod() + " " + endpoint.getPath() + " "
                + endpoint.getQualifiedMethodName() + " " + endpoint.getFilePath()).toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static final class EndpointRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RestEndpoint endpoint) {
                label.setText(endpoint.getDisplayText());
            }
            return label;
        }
    }
}
