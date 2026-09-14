package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.Host;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

final class Compiler {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+(?:final\\s+)?class\\s+(\\w+)");

    private final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    private final Map<String, byte[]> classes = new HashMap<>();
    private final ClassLoader loader = new ClassLoader(Compiler.class.getClassLoader()) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    };

    Class<?> compile(String chunk, String source) {
        return compile(chunk, source, Map.of());
    }

    Class<?> compile(String chunk, String source, Map<String, String> companions) {
        if (javac == null) {
            throw new ScriptError(chunk, "java places need a jdk to run on, this java has no compiler", null);
        }
        String name = className(chunk, source);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = javac.getStandardFileManager(diagnostics, Locale.ROOT, null);
        Map<String, ByteArrayOutputStream> output = new HashMap<>();
        ForwardingJavaFileManager<StandardJavaFileManager> files = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return output.computeIfAbsent(className, key -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject unit = new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<JavaFileObject> units = new ArrayList<>(List.of(unit));
        for (Map.Entry<String, String> companion : companions.entrySet()) {
            units.add(new SimpleJavaFileObject(URI.create("mem:///" + companion.getKey().replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return companion.getValue();
                }
            });
        }
        List<String> options = List.of("--release", "25", "-proc:none", "-classpath", classpath());
        boolean ok = javac.getTask(null, files, diagnostics, options, null, units).call();
        try {
            files.close();
        } catch (IOException ignored) {
        }
        if (!ok) throw new ScriptError(chunk, describe(diagnostics), null);
        for (Map.Entry<String, ByteArrayOutputStream> entry : output.entrySet()) classes.putIfAbsent(entry.getKey(), entry.getValue().toByteArray());
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new ScriptError(chunk, "compiled but found no class " + name, e);
        }
    }

    Class<?> load(String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static String className(String chunk, String source) {
        Matcher type = PUBLIC_CLASS.matcher(source);
        if (!type.find()) throw new ScriptError(chunk, "a java place file needs one public class", null);
        Matcher pack = PACKAGE.matcher(source);
        return pack.find() ? pack.group(1) + "." + type.group(1) : type.group(1);
    }

    private static String describe(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder out = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
            if (!out.isEmpty()) out.append('\n');
            out.append("line ").append(diagnostic.getLineNumber()).append(": ").append(diagnostic.getMessage(Locale.ROOT));
        }
        return out.isEmpty() ? "does not compile" : out.toString();
    }

    private static String classpath() {
        return String.join(File.pathSeparator, classpathEntries());
    }

    static List<String> classpathEntries() {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> anchor : List.of(Host.class, Instance.class, PlaceScript.class)) {
            CodeSource source = anchor.getProtectionDomain().getCodeSource();
            if (source == null) continue;
            try {
                entries.add(new File(source.getLocation().toURI()).getPath());
            } catch (URISyntaxException | IllegalArgumentException ignored) {
            }
        }
        String system = System.getProperty("java.class.path", "");
        if (!system.isEmpty()) {
            for (String entry : system.split(File.pathSeparator)) entries.add(entry);
        }
        return new ArrayList<>(entries);
    }
}
