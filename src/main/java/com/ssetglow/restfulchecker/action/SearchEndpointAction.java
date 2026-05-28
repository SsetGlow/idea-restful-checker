package com.ssetglow.restfulchecker.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import com.ssetglow.restfulchecker.endpoint.RestEndpointService;
import com.ssetglow.restfulchecker.ui.EndpointSearchDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SearchEndpointAction extends DumbAwareAction {
    public SearchEndpointAction() {
        super((@NlsActions.ActionText String) "Search REST Endpoint");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning REST endpoints", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<RestEndpoint> endpoints = project.getService(RestEndpointService.class).scanEndpoints();
                ApplicationManager.getApplication().invokeLater(() -> showSearch(project, endpoints), ModalityState.defaultModalityState());
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getData(CommonDataKeys.PROJECT) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static void showSearch(Project project, List<RestEndpoint> endpoints) {
        if (project.isDisposed()) {
            return;
        }
        if (endpoints.isEmpty()) {
            Messages.showInfoMessage(project, "No Spring REST endpoints were found in this project.", "idea-restful-checker");
            return;
        }
        EndpointSearchDialog dialog = new EndpointSearchDialog(project, endpoints);
        if (dialog.showAndGet() && dialog.getSelectedEndpoint() != null) {
            navigateToEndpoint(project, dialog.getSelectedEndpoint());
        }
    }

    private static void navigateToEndpoint(Project project, RestEndpoint endpoint) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(endpoint.getFilePath());
        if (file == null) {
            file = LocalFileSystem.getInstance().refreshAndFindFileByPath(endpoint.getFilePath());
        }
        if (file == null) {
            Messages.showErrorDialog(project, "Cannot find source file:\n" + endpoint.getFilePath(), "idea-restful-checker");
            return;
        }
        new OpenFileDescriptor(project, file, endpoint.getTextOffset()).navigate(true);
    }
}
