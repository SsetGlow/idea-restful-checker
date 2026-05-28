package com.ssetglow.restfulchecker.endpoint;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RestMappingPsiUtil {
    private static final String REQUEST_MAPPING = "RequestMapping";

    private RestMappingPsiUtil() {
    }

    public static boolean hasMethodMapping(PsiMethod method) {
        return methodMapping(method) != null;
    }

    @Nullable
    public static MappingDeclaration methodMapping(PsiMethod method) {
        PsiModifierList modifierList = method.getModifierList();
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String shortName = shortAnnotationName(annotation);
            String directMethod = directHttpMethod(shortName);
            if (directMethod != null) {
                return new MappingDeclaration(extractPaths(annotation), List.of(directMethod));
            }
            if (REQUEST_MAPPING.equals(shortName)) {
                return new MappingDeclaration(extractPaths(annotation), extractRequestMethods(annotation));
            }
        }
        return null;
    }

    public static List<String> classPaths(PsiClass psiClass) {
        List<String> paths = List.of("");
        List<PsiClass> hierarchy = new ArrayList<>();
        PsiClass current = psiClass;
        while (current != null) {
            hierarchy.add(0, current);
            current = current.getContainingClass();
        }

        for (PsiClass clazz : hierarchy) {
            PsiAnnotation annotation = findRequestMapping(clazz);
            if (annotation == null) {
                continue;
            }
            List<String> nextPaths = new ArrayList<>();
            for (String base : paths) {
                for (String path : extractPaths(annotation)) {
                    nextPaths.add(com.ssetglow.restfulchecker.util.PathUtil.joinPath(base, path));
                }
            }
            paths = nextPaths.isEmpty() ? paths : nextPaths;
        }
        return paths;
    }

    private static PsiAnnotation findRequestMapping(PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (REQUEST_MAPPING.equals(shortAnnotationName(annotation))) {
                return annotation;
            }
        }
        return null;
    }

    private static List<String> extractPaths(PsiAnnotation annotation) {
        Set<String> paths = new LinkedHashSet<>();
        collectStringValues(annotation.findDeclaredAttributeValue("value"), paths);
        collectStringValues(annotation.findDeclaredAttributeValue("path"), paths);
        if (paths.isEmpty()) {
            paths.add("");
        }
        return new ArrayList<>(paths);
    }

    private static List<String> extractRequestMethods(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("method");
        Set<String> methods = new LinkedHashSet<>();
        collectRequestMethodValues(value, methods);
        if (methods.isEmpty()) {
            methods.add("ANY");
        }
        return new ArrayList<>(methods);
    }

    private static void collectStringValues(@Nullable PsiAnnotationMemberValue value, Set<String> values) {
        if (value == null) {
            return;
        }
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                collectStringValues(initializer, values);
            }
            return;
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            values.add(text);
            return;
        }
        Object constantValue = JavaPsiFacade.getInstance(value.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(value);
        if (constantValue instanceof String text) {
            values.add(text);
            return;
        }
        String text = trimQuotes(value.getText());
        if (!text.isBlank()) {
            values.add(text);
        }
    }

    private static void collectRequestMethodValues(@Nullable PsiAnnotationMemberValue value, Set<String> methods) {
        if (value == null) {
            return;
        }
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                collectRequestMethodValues(initializer, methods);
            }
            return;
        }
        if (value instanceof PsiReferenceExpression referenceExpression) {
            String referenceName = referenceExpression.getReferenceName();
            if (referenceName != null) {
                methods.add(referenceName.toUpperCase(Locale.ROOT));
            }
            return;
        }
        String text = value.getText();
        int dot = text.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < text.length()) {
            text = text.substring(dot + 1);
        }
        if (!text.isBlank()) {
            methods.add(text.toUpperCase(Locale.ROOT));
        }
    }

    private static String directHttpMethod(String shortName) {
        return switch (shortName) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            default -> null;
        };
    }

    private static String shortAnnotationName(PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            qualifiedName = annotation.getNameReferenceElement() == null ? "" : annotation.getNameReferenceElement().getText();
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    private static String trimQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
