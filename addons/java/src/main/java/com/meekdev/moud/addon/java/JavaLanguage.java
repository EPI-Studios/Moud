package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JavaLanguage implements ScriptLanguage {

    @Override
    public String name() {
        return "java";
    }

    @Override
    public List<String> extensions() {
        return List.of("java");
    }

    @Override
    public ScriptEngine start(Host host) {
        return new JavaEngine(host);
    }

    @Override
    public void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {
        Path directory = place.resolve(".moud/java/moud");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("Api.java"), JavaTypes.source(api, classes));
        Files.writeString(place.resolve(".moud/java/classpath.txt"), String.join("\n", Compiler.classpathEntries()) + "\n");
    }
}
