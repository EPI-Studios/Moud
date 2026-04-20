package com.moud.server.minestom.scripting.java;

import com.moud.server.minestom.scripting.ScriptInvocationException;
import com.moud.server.minestom.scripting.ScriptObject;
import com.moud.server.minestom.scripting.api.CoreScriptApi;
import com.moud.server.minestom.util.DebugLog;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaRuntimeBridge implements AutoCloseable {
    private static final String LOG_TAG = "script-runtime";
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    private final Map<Path, Program> programs = new HashMap<>();
    private final SandboxPolicy sandbox = new SandboxPolicy();

    public Program programFor(Path scriptFile) {
        if (scriptFile == null) {
            return null;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (Exception e) {
            return null;
        }
        Program cached = programs.get(scriptFile);
        if (cached != null && cached.modifiedMs == modified) {
            return cached;
        }
        Program loaded = loadProgram(scriptFile, modified);
        if (loaded != null) {
            programs.put(scriptFile, loaded);
            DebugLog.info(LOG_TAG, (cached == null ? "loaded" : "reloaded")
                    + " language=java file=" + scriptFile.toAbsolutePath().normalize());
        }
        return loaded;
    }

    public ScriptObject createNodeInstance(Program program, CoreScriptApi api) throws ScriptInvocationException {
        if (program == null) {
            throw new ScriptInvocationException("Missing Java program");
        }
        try {
            Class<?> klass = program.loader.loadClass(program.className);
            if (!NodeScript.class.isAssignableFrom(klass)) {
                throw new ScriptInvocationException(
                        "Java script class '" + program.className + "' must extend NodeScript");
            }
            Object instance = klass.getDeclaredConstructor().newInstance();
            return new JavaScriptObject((NodeScript) instance, api);
        } catch (ScriptInvocationException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ScriptInvocationException(cause.getMessage(), cause);
        }
    }

    @Override
    public void close() {
        programs.clear();
    }

    private Program loadProgram(Path scriptFile, long modifiedMs) {
        try {
            if (!Files.isRegularFile(scriptFile)) {
                return null;
            }
            String source = Files.readString(scriptFile, StandardCharsets.UTF_8);
            String className = detectClassName(source, scriptFile);
            if (className == null) {
                DebugLog.error(LOG_TAG,
                        "load failed: " + scriptFile + ": could not find public class declaration", null);
                return null;
            }
            Map<String, byte[]> bytecode = compile(className, source, scriptFile);
            if (bytecode == null) {
                return null;
            }
            ScriptClassLoader loader = new ScriptClassLoader(bytecode, sandbox, scriptFile);
            return new Program(scriptFile, modifiedMs, className, loader);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static String detectClassName(String source, Path file) {
        Matcher matcher = CLASS_NAME_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String stem = fileName.substring(0, dot);
            if (!stem.isEmpty() && Character.isJavaIdentifierStart(stem.charAt(0))) {
                return stem;
            }
        }
        return null;
    }

    private Map<String, byte[]> compile(String className, String source, Path scriptFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": JavaCompiler unavailable", null);
            return null;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager std = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            InMemoryFileManager manager = new InMemoryFileManager(std);
            JavaFileObject unit = new StringSource(className, source);
            List<String> options = List.of(
                    "--release", String.valueOf(Runtime.version().feature()),
                    "-implicit:none",
                    "-proc:none"
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, options,
                    null, List.of(unit));
            boolean ok = task.call();
            logDiagnostics(scriptFile, diagnostics);
            Map<String, byte[]> collected = manager.collect();
            if (!ok || collected.isEmpty()) {
                return null;
            }
            return collected;
        } catch (IOException e) {
            DebugLog.error(LOG_TAG, "compile failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static void logDiagnostics(Path scriptFile, DiagnosticCollector<JavaFileObject> diagnostics) {
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String msg = scriptFile + ":" + d.getLineNumber() + ":" + d.getColumnNumber()
                    + " " + d.getKind() + " " + d.getMessage(Locale.ROOT);
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                DebugLog.error(LOG_TAG, msg, null);
            } else {
                DebugLog.info(LOG_TAG, msg);
            }
        }
    }

    public static final class Program {
        private final Path file;
        private final long modifiedMs;
        private final String className;
        private final ScriptClassLoader loader;

        private Program(Path file, long modifiedMs, String className, ScriptClassLoader loader) {
            this.file = file;
            this.modifiedMs = modifiedMs;
            this.className = className;
            this.loader = loader;
        }

        public Path file() {
            return file;
        }

        public long modifiedMs() {
            return modifiedMs;
        }

        public String className() {
            return className;
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String source;

        StringSource(String className, String source) {
            super(URI.create("string:///" + className + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class BytecodeOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        BytecodeOutput(String name) {
            super(URI.create("mem:///" + name.replace('.', '/') + ".class"), Kind.CLASS);
        }

        @Override
        public java.io.OutputStream openOutputStream() {
            return buffer;
        }

        byte[] bytes() {
            return buffer.toByteArray();
        }
    }

    private static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, BytecodeOutput> outputs = new HashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className,
                                                   JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
            if (kind == JavaFileObject.Kind.CLASS) {
                BytecodeOutput output = new BytecodeOutput(className);
                outputs.put(className, output);
                return output;
            }
            try {
                return super.getJavaFileForOutput(location, className, kind, sibling);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        Map<String, byte[]> collect() {
            Map<String, byte[]> result = new HashMap<>();
            for (Map.Entry<String, BytecodeOutput> e : outputs.entrySet()) {
                result.put(e.getKey(), e.getValue().bytes());
            }
            return result;
        }
    }

    static final class SandboxPolicy {
        private static final List<String> ALLOWED_PACKAGE_PREFIXES = List.of(
                "java.lang.",
                "java.util.",
                "java.math.",
                "java.time.",
                "java.text.",
                "java.nio.charset.",
                "com.moud.server.minestom.scripting.java.",
                "com.moud.server.minestom.scripting.api.",
                "com.moud.server.minestom.scripting.player.",
                "com.moud.server.minestom.scripting.input.",
                "com.moud.server.minestom.scripting.signal.",
                "com.moud.server.minestom.physics.",
                "com.moud.server.minestom.scripting.ScriptCallable",
                "com.moud.server.minestom.scripting.ScriptCallback",
                "com.moud.server.minestom.scripting.ScriptInvocationException",
                "com.moud.server.minestom.scripting.ScriptObject"
        );

        private static final List<String> ALLOWED_EXACT = List.of(
                "java.lang",
                "java.util",
                "java.math",
                "java.time",
                "java.text"
        );

        boolean allows(String binaryName) {
            if (binaryName == null) {
                return false;
            }
            if (ALLOWED_EXACT.contains(binaryName)) {
                return true;
            }
            for (String prefix : ALLOWED_PACKAGE_PREFIXES) {
                if (binaryName.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class ScriptClassLoader extends ClassLoader {
        private final Map<String, byte[]> bytecode;
        private final SandboxPolicy sandbox;
        private final Path scriptFile;

        ScriptClassLoader(Map<String, byte[]> bytecode, SandboxPolicy sandbox, Path scriptFile) {
            super(NodeScript.class.getClassLoader());
            this.bytecode = Map.copyOf(bytecode);
            this.sandbox = sandbox;
            this.scriptFile = scriptFile;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = bytecode.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && bytecode.containsKey(name)) {
                loaded = findClass(name);
            }
            if (loaded == null) {
                if (!sandbox.allows(name)) {
                    throw new ClassNotFoundException(
                            "Class '" + name + "' is not in the Moud script sandbox allowlist (script: "
                                    + scriptFile + ")");
                }
                loaded = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

}
