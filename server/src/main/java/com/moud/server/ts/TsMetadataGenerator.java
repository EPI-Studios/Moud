package com.moud.server.ts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TsMetadataGenerator {
    private TsMetadataGenerator() {}

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Path output = args.length > 0
                ? Path.of(args[0])
                : Path.of("packages", "sdk", "generated", "server-api.json");
        generate(output);
    }

    public static void generate(Path output) throws IOException, ClassNotFoundException {
        List<ApiClass> classes = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages("com.moud.server")
                .scan()) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(TsExpose.class.getName())) {
                Class<?> clazz = Class.forName(classInfo.getName(), false, loader);
                TsExpose annotation = clazz.getAnnotation(TsExpose.class);
                String exportName = annotation != null && !annotation.name().isBlank()
                        ? annotation.name()
                        : clazz.getSimpleName();

                List<ApiMethod> methods = new ArrayList<>();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        continue;
                    }
                    if (!method.isAnnotationPresent(HostAccess.Export.class)) {
                        continue;
                    }
                    List<ApiParameter> params = new ArrayList<>();
                    Parameter[] parameters = method.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];
                        String paramName = parameter.isNamePresent() ? parameter.getName() : "arg" + i;
                        params.add(new ApiParameter(paramName, simplifyType(parameter.getType())));
                    }
                    methods.add(new ApiMethod(method.getName(), simplifyType(method.getReturnType()), params));
                }

                if (!methods.isEmpty()) {
                    methods.sort(Comparator.comparing(ApiMethod::name));
                    classes.add(new ApiClass(exportName, clazz.getName(), methods));
                }
            }
        }

        classes.sort(Comparator.comparing(ApiClass::name));

        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.toFile(), classes);
        System.out.printf("Generated %d API metadata entries into %s%n", classes.size(), output);
    }

    private static String simplifyType(Class<?> type) {
        if (type.isArray()) {
            return simplifyType(type.getComponentType()) + "[]";
        }
        if (type.isPrimitive()) {
            return type.getName();
        }
        return type.getSimpleName();
    }

    public record ApiClass(String name, String qualifiedName, List<ApiMethod> methods) {}

    public record ApiMethod(String name, String returnType, List<ApiParameter> parameters) {}

    public record ApiParameter(String name, String type) {}
}
