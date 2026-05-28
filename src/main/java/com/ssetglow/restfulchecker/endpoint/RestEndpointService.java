package com.ssetglow.restfulchecker.endpoint;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.ssetglow.restfulchecker.config.ProjectConfigResolver;
import com.ssetglow.restfulchecker.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class RestEndpointService {
    private final Project project;

    public RestEndpointService(Project project) {
        this.project = project;
    }

    public List<RestEndpoint> scanEndpoints() {
        Map<String, String> projectConfig = ProjectConfigResolver.resolve(project);
        return ReadAction.compute(() -> scanEndpointsInReadAction(projectConfig));
    }

    @Nullable
    public RestEndpoint findEndpointAtCaret(Editor editor, PsiFile file) {
        Map<String, String> projectConfig = ProjectConfigResolver.resolve(project);
        return ReadAction.compute(() -> {
            PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
            while (element != null && !(element instanceof PsiMethod)) {
                element = element.getParent();
            }
            if (element instanceof PsiMethod method) {
                List<RestEndpoint> endpoints = endpointsForMethod(method, projectConfig);
                return endpoints.isEmpty() ? null : endpoints.get(0);
            }
            return null;
        });
    }

    @Nullable
    public RestEndpoint firstEndpointForMethod(PsiMethod method) {
        Map<String, String> projectConfig = ProjectConfigResolver.resolve(project);
        List<RestEndpoint> endpoints = endpointsForMethod(method, projectConfig);
        return endpoints.isEmpty() ? null : endpoints.get(0);
    }

    private List<RestEndpoint> scanEndpointsInReadAction(Map<String, String> projectConfig) {
        List<RestEndpoint> endpoints = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (VirtualFile virtualFile : FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, JavaFileType.INSTANCE)) {
                continue;
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (!(psiFile instanceof PsiJavaFile)) {
                continue;
            }
            psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethod(PsiMethod method) {
                    endpoints.addAll(endpointsForMethod(method, projectConfig));
                    super.visitMethod(method);
                }
            });
        }
        endpoints.sort(Comparator.comparing(RestEndpoint::getPath).thenComparing(RestEndpoint::getHttpMethod));
        return endpoints;
    }

    private List<RestEndpoint> endpointsForMethod(PsiMethod method, Map<String, String> projectConfig) {
        MappingDeclaration methodMapping = RestMappingPsiUtil.methodMapping(method);
        if (methodMapping == null) {
            return List.of();
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return List.of();
        }
        List<String> classPaths = RestMappingPsiUtil.classPaths(containingClass);
        String contextPath = ProjectConfigResolver.contextPath(projectConfig);
        String servletPath = ProjectConfigResolver.servletPath(projectConfig);
        String className = containingClass.getQualifiedName() == null ? containingClass.getName() : containingClass.getQualifiedName();
        String filePath = method.getContainingFile() == null || method.getContainingFile().getVirtualFile() == null
                ? ""
                : method.getContainingFile().getVirtualFile().getPath();

        List<RestEndpoint> endpoints = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodMapping.paths()) {
                String path = PathUtil.joinPath(contextPath, servletPath, classPath, methodPath);
                for (String httpMethod : methodMapping.methods()) {
                    endpoints.add(new RestEndpoint(httpMethod, path, className, method.getName(), filePath, method.getTextOffset()));
                }
            }
        }
        return endpoints;
    }
}
