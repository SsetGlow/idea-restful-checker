package com.ssetglow.restfulchecker.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import com.ssetglow.restfulchecker.endpoint.RestEndpointService;
import com.ssetglow.restfulchecker.ui.CallEndpointDialog;
import org.jetbrains.annotations.NotNull;

public final class CallEndpointAtCaretAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || file == null) {
            return;
        }

        RestEndpoint endpoint = project.getService(RestEndpointService.class).findEndpointAtCaret(editor, file);
        if (endpoint == null) {
            Messages.showInfoMessage(project, "Place the caret inside a Spring REST mapping method.", "restful-checker");
            return;
        }
        new CallEndpointDialog(project, endpoint).show();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean enabled = event.getData(CommonDataKeys.PROJECT) != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PSI_FILE) != null;
        event.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
