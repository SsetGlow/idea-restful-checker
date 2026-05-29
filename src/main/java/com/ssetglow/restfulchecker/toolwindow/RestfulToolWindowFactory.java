package com.ssetglow.restfulchecker.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import com.ssetglow.restfulchecker.endpoint.RestEndpointService;
import com.ssetglow.restfulchecker.settings.RestCheckerGlobalSettings;
import com.ssetglow.restfulchecker.ui.CallEndpointDialog;
import com.ssetglow.restfulchecker.ui.EndpointListPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class RestfulToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Restful");
        toolWindow.setStripeTitle("Restful");
        toolWindow.setIcon(IconLoader.getIcon("/icons/restfulToolWindow.svg", RestfulToolWindowFactory.class));
        RestfulToolWindowPanel panel = new RestfulToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private static final class RestfulToolWindowPanel extends JPanel {
        private final Project project;
        private final EndpointListPanel endpointListPanel;
        private final JBLabel statusLabel = new JBLabel("Scanning...");

        private RestfulToolWindowPanel(Project project) {
            super(new BorderLayout(0, 0));
            this.project = project;
            endpointListPanel = new EndpointListPanel(
                    List.of(),
                    true,
                    endpoint -> new CallEndpointDialog(project, endpoint).show(),
                    true
            );
            add(createTopPanel(), BorderLayout.NORTH);
            add(endpointListPanel, BorderLayout.CENTER);
            reloadEndpoints();
        }

        private JComponent createTopPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));
            DefaultActionGroup actionGroup = new DefaultActionGroup();
            actionGroup.add(new ToolAction("Global hosts", "Global host configuration", AllIcons.General.Settings) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    editHosts();
                }
            });
            actionGroup.add(new ToolAction("Headers", "Global header configuration", AllIcons.Nodes.Property) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    editHeaders();
                }
            });
            actionGroup.addSeparator();
            actionGroup.add(new ToolAction("Refresh", "Refresh REST endpoints", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    reloadEndpoints();
                }
            });
            ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("RestfulChecker.ToolWindow", actionGroup, true);
            toolbar.setTargetComponent(this);
            panel.add(toolbar.getComponent(), BorderLayout.WEST);
            panel.add(statusLabel, BorderLayout.EAST);
            return panel;
        }

        private void reloadEndpoints() {
            statusLabel.setText("Scanning...");
            AppExecutorUtil.getAppExecutorService().execute(() -> {
                List<RestEndpoint> endpoints = project.getService(RestEndpointService.class).scanEndpoints();
                SwingUtilities.invokeLater(() -> {
                    if (project.isDisposed()) {
                        return;
                    }
                    endpointListPanel.setEndpoints(endpoints);
                    statusLabel.setText(endpoints.size() + " endpoints");
                });
            });
        }

        private void editHosts() {
            RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
            TextSettingsDialog dialog = new TextSettingsDialog(
                    "Global hosts",
                    "One host per line",
                    String.join("\n", settings.getHosts()),
                    8
            );
            if (dialog.showAndGet()) {
                settings.setHosts(parseLines(dialog.getText()));
            }
        }

        private void editHeaders() {
            RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
            TextSettingsDialog dialog = new TextSettingsDialog(
                    "Global headers",
                    "Header lines, for example: Content-Type: application/json",
                    settings.getDefaultHeaders(),
                    10
            );
            if (dialog.showAndGet()) {
                settings.setDefaultHeaders(dialog.getText());
            }
        }

        private static List<String> parseLines(String text) {
            if (text == null || text.isBlank()) {
                return new ArrayList<>();
            }
            return Arrays.stream(text.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private abstract static class ToolAction extends DumbAwareAction {
        private ToolAction(String text, String description, javax.swing.Icon icon) {
            super(text, description, icon);
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            Presentation presentation = event.getPresentation();
            presentation.setEnabled(true);
        }
    }

    private static final class TextSettingsDialog extends DialogWrapper {
        private final String label;
        private final JBTextArea textArea;

        private TextSettingsDialog(String title, String label, String text, int rows) {
            super(true);
            this.label = label;
            textArea = new JBTextArea(rows, 64);
            textArea.setLineWrap(false);
            textArea.setText(text == null ? "" : text);
            setTitle(title);
            init();
        }

        private String getText() {
            return textArea.getText();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.add(new JBLabel(label), BorderLayout.NORTH);
            JBScrollPane scrollPane = new JBScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(520, 220));
            panel.add(scrollPane, BorderLayout.CENTER);
            return panel;
        }

        @Override
        protected Action @NotNull [] createActions() {
            return new Action[]{getOKAction(), getCancelAction()};
        }
    }
}
