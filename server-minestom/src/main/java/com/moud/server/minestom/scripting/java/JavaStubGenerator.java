package com.moud.server.minestom.scripting.java;

import com.moud.server.minestom.scripting.api.CoreScriptApi;
import com.moud.server.minestom.scripting.api.modules.CameraApi;
import com.moud.server.minestom.scripting.api.modules.CursorApi;
import com.moud.server.minestom.scripting.api.modules.HttpApi;
import com.moud.server.minestom.scripting.api.modules.MessagingApi;
import com.moud.server.minestom.scripting.api.modules.NodeApi;
import com.moud.server.minestom.scripting.api.modules.ParticlesApi;
import com.moud.server.minestom.scripting.api.modules.PersistApi;
import com.moud.server.minestom.scripting.api.modules.PhysicsApi;
import com.moud.server.minestom.scripting.api.modules.PlayerApi;
import com.moud.server.minestom.scripting.api.modules.SceneApi;
import com.moud.server.minestom.scripting.player.InputEvent;
import com.moud.server.minestom.scripting.player.PlayerInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JavaStubGenerator {
    private static final List<Class<?>> API_TYPES = List.of(
            CoreScriptApi.class,
            NodeApi.class,
            SceneApi.class,
            PhysicsApi.class,
            PlayerApi.class,
            CameraApi.class,
            CursorApi.class,
            MessagingApi.class,
            ParticlesApi.class,
            PersistApi.class,
            HttpApi.class,
            InputEvent.class,
            PlayerInfo.class
    );

    public void generate(Path typesRoot) throws Exception {
        Path srcRoot = typesRoot.resolve("java");
        Files.createDirectories(srcRoot);
        writeNodeScriptStub(srcRoot);
        for (Class<?> type : API_TYPES) {
            writeApiStub(srcRoot, type);
        }
    }

    private void writeNodeScriptStub(Path srcRoot) throws Exception {
        String body = """
                package com.moud.server.minestom.scripting.java;

                import com.moud.server.minestom.scripting.api.CoreScriptApi;
                import com.moud.server.minestom.scripting.api.modules.*;
                import com.moud.server.minestom.scripting.player.InputEvent;

                // Auto-generated stub. Do not edit.
                public abstract class NodeScript {
                    protected CoreScriptApi core;
                    protected NodeApi node;
                    protected SceneApi scene;
                    protected PhysicsApi physics;
                    protected PlayerApi players;
                    protected CameraApi camera;
                    protected CursorApi cursor;
                    protected MessagingApi msg;
                    protected ParticlesApi particles;
                    protected PersistApi persist;
                    protected HttpApi http;

                    public void onReady() {}
                    public void onEnterTree() {}
                    public void onExitTree() {}
                    public void onProcess(double dt) {}
                    public void onPhysicsProcess(double dt) {}
                    public void onInput(InputEvent event) {}
                    public void onSignal(String name, Object value) {}

                    public final long selfId() { return 0L; }
                    public final void log(String message) {}
                }
                """;
        Path file = srcRoot.resolve("com/moud/server/minestom/scripting/java/NodeScript.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private void writeApiStub(Path srcRoot, Class<?> type) throws Exception {
        String pkg = type.getPackageName();
        Path file = srcRoot.resolve(pkg.replace('.', '/')).resolve(type.getSimpleName() + ".java");
        Files.createDirectories(file.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("// Auto-generated stub. Do not edit.\n");
        sb.append("public final class ").append(type.getSimpleName()).append(" {\n");

        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        Set<String> emitted = new HashSet<>();
        for (Method m : methods) {
            if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            String sig = signature(m);
            if (!emitted.add(sig)) {
                continue;
            }
            sb.append("    public ").append(typeName(m.getReturnType())).append(' ').append(m.getName()).append('(');
            Parameter[] params = m.getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeName(params[i].getType())).append(' ').append("arg").append(i);
            }
            sb.append(") { ").append(defaultReturn(m.getReturnType())).append(" }\n");
        }
        sb.append("}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(typeName(p)).append(',');
        }
        return sb.append(')').toString();
    }

    private static String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        if (type.isPrimitive() || type == String.class) {
            return type.getName();
        }
        String name = type.getName();
        if (name.startsWith("com.moud.") || name.startsWith("java.")) {
            return name;
        }
        return "java.lang.Object";
    }

    private static String defaultReturn(Class<?> type) {
        if (type == void.class) return "";
        if (type == boolean.class) return "return false;";
        if (type == byte.class || type == short.class || type == int.class) return "return 0;";
        if (type == long.class) return "return 0L;";
        if (type == float.class) return "return 0f;";
        if (type == double.class) return "return 0.0;";
        if (type == char.class) return "return '\\0';";
        return "return null;";
    }
}
