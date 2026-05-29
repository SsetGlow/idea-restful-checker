package com.ssetglow.restfulchecker.endpoint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EndpointRequestDefaults {
    private static final int MAX_OBJECT_DEPTH = 2;
    private static final Set<String> STRING_TYPES = Set.of(
            CommonClassNames.JAVA_LANG_STRING,
            "java.lang.Character",
            "java.util.UUID",
            "java.math.BigInteger",
            "java.math.BigDecimal",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime",
            "java.util.Date"
    );
    private static final Set<String> NUMBER_TYPES = Set.of(
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double"
    );

    private EndpointRequestDefaults() {
    }

    public static RequestDefaults forEndpoint(Project project, RestEndpoint endpoint) {
        return ApplicationManager.getApplication().runReadAction((Computable<RequestDefaults>) () -> {
            PsiMethod method = resolveMethod(project, endpoint);
            return method == null ? RequestDefaults.empty() : fromMethod(method);
        });
    }

    private static RequestDefaults fromMethod(PsiMethod method) {
        Map<String, String> requestParams = new LinkedHashMap<>();
        String body = "";
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            PsiAnnotation requestParam = findExplicitAnnotation(parameter, "RequestParam");
            if (requestParam != null) {
                collectRequestParam(parameter, requestParam, requestParams);
            }
            PsiAnnotation requestBody = findExplicitAnnotation(parameter, "RequestBody");
            if (requestBody != null && body.isBlank()) {
                body = defaultJson(parameter.getType(), new LinkedHashSet<>(), 0);
            }
        }
        return new RequestDefaults(requestParams, body);
    }

    private static void collectRequestParam(PsiParameter parameter, PsiAnnotation annotation, Map<String, String> requestParams) {
        String explicitName = requestParamName(annotation);
        boolean hasExplicitName = explicitName != null && !explicitName.isBlank();
        if (!hasExplicitName && isExpandableBean(parameter.getType())) {
            collectBeanFields(parameter.getType(), requestParams);
            return;
        }

        String name = hasExplicitName ? explicitName : parameter.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        requestParams.put(name, explicitDefaultValue(annotation, parameter.getType()));
    }

    private static void collectBeanFields(PsiType type, Map<String, String> requestParams) {
        PsiClass psiClass = resolveClass(type);
        if (psiClass == null) {
            return;
        }
        for (PsiField field : psiClass.getAllFields()) {
            if (shouldSkipField(field)) {
                continue;
            }
            requestParams.putIfAbsent(field.getName(), defaultPlainValue(field.getType()));
        }
    }

    private static String explicitDefaultValue(PsiAnnotation annotation, PsiType type) {
        String defaultValue = stringAttribute(annotation, "defaultValue");
        if (defaultValue != null && !defaultValue.isBlank()) {
            return defaultValue;
        }
        return defaultPlainValue(type);
    }

    @Nullable
    private static String requestParamName(PsiAnnotation annotation) {
        String name = stringAttribute(annotation, "name");
        return name == null || name.isBlank() ? stringAttribute(annotation, "value") : name;
    }

    @Nullable
    private static String stringAttribute(PsiAnnotation annotation, String attributeName) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attributeName);
        if (value == null) {
            return null;
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            return text;
        }
        Object constantValue = JavaPsiFacade.getInstance(value.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(value);
        if (constantValue instanceof String text) {
            return text;
        }
        return trimQuotes(value.getText());
    }

    @Nullable
    private static PsiAnnotation findExplicitAnnotation(PsiParameter parameter, String shortName) {
        for (PsiAnnotation annotation : parameter.getAnnotations()) {
            if (shortName.equals(shortAnnotationName(annotation))) {
                return annotation;
            }
        }
        return null;
    }

    private static String shortAnnotationName(PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            qualifiedName = annotation.getNameReferenceElement() == null ? "" : annotation.getNameReferenceElement().getText();
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    private static String defaultJson(PsiType type, Set<String> visiting, int depth) {
        if (type instanceof PsiArrayType) {
            return "[]";
        }
        if (isBoolean(type)) {
            return "false";
        }
        if (isNumber(type)) {
            return "0";
        }
        if (isStringLike(type)) {
            return "\"\"";
        }
        PsiClass psiClass = resolveClass(type);
        if (psiClass == null) {
            return "{}";
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (psiClass.isEnum()) {
            return "\"" + jsonEscape(firstEnumValue(psiClass)) + "\"";
        }
        if (isCollection(psiClass)) {
            return "[]";
        }
        if (isMap(psiClass)) {
            return "{}";
        }
        if (isOptional(qualifiedName)) {
            return "null";
        }
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
            return "{}";
        }
        if (qualifiedName != null && qualifiedName.startsWith("java.")) {
            return "\"\"";
        }
        if (qualifiedName == null || depth >= MAX_OBJECT_DEPTH || visiting.contains(qualifiedName)) {
            return "{}";
        }

        visiting.add(qualifiedName);
        String objectJson = defaultObjectJson(psiClass, visiting, depth);
        visiting.remove(qualifiedName);
        return objectJson;
    }

    private static String defaultObjectJson(PsiClass psiClass, Set<String> visiting, int depth) {
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (PsiField field : psiClass.getAllFields()) {
            if (!shouldSkipField(field)) {
                fieldValues.putIfAbsent(field.getName(), defaultJson(field.getType(), visiting, depth + 1));
            }
        }
        if (fieldValues.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('{').append('\n');
        int index = 0;
        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            builder.append(indent(depth + 1))
                    .append('"')
                    .append(jsonEscape(entry.getKey()))
                    .append("\": ")
                    .append(entry.getValue());
            if (++index < fieldValues.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(indent(depth)).append('}');
        return builder.toString();
    }

    private static String defaultPlainValue(PsiType type) {
        if (isBoolean(type)) {
            return "false";
        }
        if (isNumber(type)) {
            return "0";
        }
        PsiClass psiClass = resolveClass(type);
        if (psiClass != null && psiClass.isEnum()) {
            return firstEnumValue(psiClass);
        }
        String qualifiedName = psiClass == null ? null : psiClass.getQualifiedName();
        if (type instanceof PsiArrayType || isCollection(psiClass)) {
            return "[]";
        }
        if (isMap(psiClass) || isExpandableBean(type)) {
            return "{}";
        }
        return "";
    }

    private static boolean isExpandableBean(PsiType type) {
        PsiClass psiClass = resolveClass(type);
        if (psiClass == null || psiClass.isEnum()) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName != null
                && !isStringLike(type)
                && !isBoolean(type)
                && !isNumber(type)
                && !isCollection(psiClass)
                && !isMap(psiClass)
                && !isOptional(qualifiedName)
                && !qualifiedName.startsWith("java.");
    }

    private static boolean isStringLike(PsiType type) {
        PsiClass psiClass = resolveClass(type);
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName != null && STRING_TYPES.contains(qualifiedName);
    }

    private static boolean isBoolean(PsiType type) {
        if (PsiTypes.booleanType().equals(type)) {
            return true;
        }
        PsiClass psiClass = resolveClass(type);
        return psiClass != null && CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName());
    }

    private static boolean isNumber(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            String text = type.getCanonicalText();
            return !"boolean".equals(text) && !"char".equals(text) && !"void".equals(text);
        }
        PsiClass psiClass = resolveClass(type);
        return psiClass != null && NUMBER_TYPES.contains(psiClass.getQualifiedName());
    }

    @Nullable
    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }

    private static boolean isCollection(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName != null && (qualifiedName.equals("java.util.Collection")
                || qualifiedName.equals("java.util.List")
                || qualifiedName.equals("java.util.Set")
                || qualifiedName.equals("java.lang.Iterable")
                || qualifiedName.equals("java.util.Queue")
                || qualifiedName.equals("java.util.Deque")
                || InheritanceUtil.isInheritor(psiClass, "java.util.Collection")
                || InheritanceUtil.isInheritor(psiClass, "java.lang.Iterable"));
    }

    private static boolean isMap(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName != null && (qualifiedName.equals("java.util.Map")
                || InheritanceUtil.isInheritor(psiClass, "java.util.Map"));
    }

    private static boolean isOptional(@Nullable String qualifiedName) {
        return qualifiedName != null && qualifiedName.equals("java.util.Optional");
    }

    private static boolean shouldSkipField(PsiField field) {
        return field.hasModifierProperty(PsiModifier.STATIC)
                || field.hasModifierProperty(PsiModifier.TRANSIENT);
    }

    private static String firstEnumValue(PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            if (field instanceof PsiEnumConstant) {
                return field.getName();
            }
        }
        return "";
    }

    @Nullable
    private static PsiMethod resolveMethod(Project project, RestEndpoint endpoint) {
        if (endpoint.getFilePath().isBlank()) {
            return null;
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(endpoint.getFilePath());
        if (virtualFile == null) {
            return null;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile == null) {
            return null;
        }
        PsiMethod methodAtOffset = methodAtOffset(psiFile, endpoint.getTextOffset());
        if (methodMatches(methodAtOffset, endpoint)) {
            return methodAtOffset;
        }
        return matchingMethodInFile(psiFile, endpoint);
    }

    @Nullable
    private static PsiMethod methodAtOffset(PsiFile psiFile, int offset) {
        if (psiFile.getTextLength() == 0) {
            return null;
        }
        int safeOffset = Math.max(0, Math.min(offset, psiFile.getTextLength() - 1));
        PsiElement element = psiFile.findElementAt(safeOffset);
        return element == null ? null : PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }

    @Nullable
    private static PsiMethod matchingMethodInFile(PsiFile psiFile, RestEndpoint endpoint) {
        MethodMatchVisitor visitor = new MethodMatchVisitor(endpoint);
        psiFile.accept(visitor);
        return visitor.bestMatch;
    }

    private static boolean methodMatches(@Nullable PsiMethod method, RestEndpoint endpoint) {
        if (method == null || !method.getName().equals(endpoint.getMethodName())) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass == null ? "" : containingClass.getQualifiedName();
        return endpoint.getClassName().isBlank() || endpoint.getClassName().equals(className);
    }

    private static String indent(int depth) {
        return "  ".repeat(Math.max(0, depth));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    public record RequestDefaults(Map<String, String> requestParams, String body) {
        private static RequestDefaults empty() {
            return new RequestDefaults(Map.of(), "");
        }
    }

    private static final class MethodMatchVisitor extends JavaRecursiveElementWalkingVisitor {
        private final RestEndpoint endpoint;
        private PsiMethod bestMatch;
        private int bestDistance = Integer.MAX_VALUE;

        private MethodMatchVisitor(RestEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void visitMethod(PsiMethod method) {
            if (methodMatches(method, endpoint)) {
                int distance = Math.abs(method.getTextOffset() - endpoint.getTextOffset());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = method;
                }
            }
            super.visitMethod(method);
        }
    }
}
