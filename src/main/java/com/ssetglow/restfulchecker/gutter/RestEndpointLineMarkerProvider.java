package com.ssetglow.restfulchecker.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Function;
import com.ssetglow.restfulchecker.endpoint.RestEndpoint;
import com.ssetglow.restfulchecker.endpoint.RestEndpointService;
import com.ssetglow.restfulchecker.endpoint.RestMappingPsiUtil;
import com.ssetglow.restfulchecker.ui.CallEndpointDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.List;

public final class RestEndpointLineMarkerProvider implements LineMarkerProvider {
    private static final Icon CALL_ENDPOINT_ICON = IconLoader.getIcon(
            "/icons/restEndpointSend.svg",
            RestEndpointLineMarkerProvider.class
    );

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiMethod method)) {
            return null;
        }
        if (!RestMappingPsiUtil.hasMethodMapping(method)) {
            return null;
        }
        Function<PsiElement, String> tooltipProvider = ignored -> "Call REST Endpoint";
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                CALL_ENDPOINT_ICON,
                tooltipProvider,
                (event, psiElement) -> {
                    if (!(psiElement.getParent() instanceof PsiMethod targetMethod)) {
                        return;
                    }
                    RestEndpoint endpoint = psiElement.getProject()
                            .getService(RestEndpointService.class)
                            .firstEndpointForMethod(targetMethod);
                    if (endpoint != null) {
                        new CallEndpointDialog(psiElement.getProject(), endpoint).show();
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Call REST Endpoint"
        );
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    }
}
