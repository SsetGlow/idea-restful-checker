package com.ssetglow.restfulchecker.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.util.List;

public final class EndpointSearchDialog extends DialogWrapper {
    private final List<RestEndpoint> endpoints;
    private EndpointListPanel endpointListPanel;
    private RestEndpoint selectedEndpoint;

    public EndpointSearchDialog(Project project, List<RestEndpoint> endpoints) {
        super(project, true);
        this.endpoints = endpoints;
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
        endpointListPanel = new EndpointListPanel(endpoints, true, ignored -> doOKAction());
        endpointListPanel.setPreferredSize(new Dimension(760, 420));
        SwingUtilities.invokeLater(endpointListPanel::requestSearchFocus);
        return endpointListPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return endpointListPanel == null ? null : endpointListPanel.getPreferredFocusedComponent();
    }

    @Override
    protected void doOKAction() {
        RestEndpoint endpoint = endpointListPanel == null ? null : endpointListPanel.getSelectedEndpoint();
        if (endpoint == null) {
            return;
        }
        selectedEndpoint = endpoint;
        super.doOKAction();
    }

    @Override
    public void dispose() {
        if (endpointListPanel != null) {
            endpointListPanel.dispose();
        }
        super.dispose();
    }
}
