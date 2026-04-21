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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaRuntimeBridge implements AutoCloseable {
    private static final String LOG_TAG = "script-runtime";
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    private final Map<Path, Program> programs = new HashMap<>();

    public Program programFor(Path scriptFile) {
        if (scriptFile == null) {
            return null;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": file missing or unreadable (" + e.getMessage() + ")");
            return null;
        }
        Program cached = programs.get(scriptFile);
        if (cached != null && cached.modifiedMs == modified) {
            return cached;
        }
        Program loaded = loadProgram(scriptFile, modified);
        if (loaded != null) {
            programs.put(scriptFile, loaded);
            DebugLog.debug(LOG_TAG, (cached == null ? "loaded" : "reloaded")
                    + " java " + scriptFile.getFileName());
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
                DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": not a regular file");
                return null;
            }
            String source = Files.readString(scriptFile, StandardCharsets.UTF_8);
            String className = detectClassName(source, scriptFile);
            if (className == null) {
                DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": no public class declaration found");
                return null;
            }
            Map<String, byte[]> bytecode = compile(className, source, scriptFile);
            if (bytecode == null) {
                return null;
            }
            ScriptClassLoader loader = new ScriptClassLoader(bytecode, scriptFile);
            return new Program(scriptFile, modifiedMs, className, loader);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": load failed: " + e.getMessage());
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
            DebugLog.error(LOG_TAG, scriptFile.getFileName()
                    + ": JavaCompiler unavailable - launch Moud under a JDK, not a JRE");
            return null;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager std = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<Path> classpath = collectRuntimeClasspath();
            if (DebugLog.enabled()) {
                DebugLog.debug(LOG_TAG, "java classpath entries=" + classpath.size()
                        + " for " + scriptFile.getFileName());
                for (Path p : classpath) {
                    DebugLog.debug(LOG_TAG, "  cp: " + p);
                }
            }
            if (!classpath.isEmpty()) {
                try {
                    std.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
                } catch (Throwable t) {
                    DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": classpath setup failed: " + t.getMessage());
                }
            }
            InMemoryFileManager manager = new InMemoryFileManager(std);
            JavaFileObject unit = new StringSource(className, source);
            List<String> options = List.of(
                    "--release", String.valueOf(Runtime.version().feature()),
                    "-implicit:none",
                    "-proc:none",
                    "-g:source,lines"
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, options,
                    null, List.of(unit));
            boolean ok;
            try {
                ok = task.call();
            } catch (Throwable t) {
                logDiagnostics(scriptFile, diagnostics);
                DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": compiler crashed: " + t.getMessage());
                return null;
            }
            logDiagnostics(scriptFile, diagnostics);
            Map<String, byte[]> collected = manager.collect();
            if (!ok || collected.isEmpty()) {
                return null;
            }
            return collected;
        } catch (Throwable e) {
            DebugLog.error(LOG_TAG, scriptFile.getFileName() + ": compile failed: " + e.getMessage());
            return null;
        }
    }

    private static List<Path> collectRuntimeClasspath() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();

        String sysCp = System.getProperty("java.class.path", "");
        for (String entry : sysCp.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                try { paths.add(Path.of(entry)); } catch (Exception ignored) { }
            }
        }

        for (ClassLoader cl = Thread.currentThread().getContextClassLoader();
             cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    try { paths.add(Path.of(url.toURI())); } catch (Exception ignored) { }
                }
            }
        }

        addCodeSource(paths, NodeScript.class);
        addCodeSource(paths, CoreScriptApi.class);

        try {
            Class<?> loaderCls = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderCls.getMethod("getInstance").invoke(null);
            Iterable<?> mods = (Iterable<?>) loaderCls.getMethod("getAllMods").invoke(loader);
            for (Object mod : mods) {
                try {
                    Object origin = mod.getClass().getMethod("getOrigin").invoke(mod);
                    if (origin != null) {
                        Object originPaths = origin.getClass().getMethod("getPaths").invoke(origin);
                        if (originPaths instanceof Iterable<?> it) {
                            for (Object p : it) {
                                if (p instanceof Path path && Files.exists(path)) paths.add(path);
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }

        paths.removeIf(p -> {
            try { return !Files.exists(p); } catch (Throwable t) { return true; }
        });

        return new ArrayList<>(paths);
    }

    private static void addCodeSource(Set<Path> out, Class<?> klass) {
        try {
            URL loc = klass.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                out.add(Path.of(loc.toURI()));
            }
        } catch (Exception ignored) { }
    }

    private static void logDiagnostics(Path scriptFile, DiagnosticCollector<JavaFileObject> diagnostics) {
        String fileName = scriptFile.getFileName().toString();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            long line = d.getLineNumber();
            long col = d.getColumnNumber();
            String loc = line > 0 ? fileName + ":" + line + (col > 0 ? ":" + col : "") : fileName;
            String msg = loc + ": " + d.getMessage(Locale.ROOT);
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                DebugLog.error(LOG_TAG, msg);
            } else if (d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                DebugLog.warn(LOG_TAG, msg);
            } else {
                DebugLog.debug(LOG_TAG, msg);
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

    static final class ScriptClassLoader extends ClassLoader {
        private final Map<String, byte[]> bytecode;
        private final Path scriptFile;

        ScriptClassLoader(Map<String, byte[]> bytecode, Path scriptFile) {
            super(NodeScript.class.getClassLoader());
            this.bytecode = Map.copyOf(bytecode);
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

        public Path scriptFile() {
            return scriptFile;
        }
    }
}
