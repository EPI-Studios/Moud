package com.meekdev.moud.addon.revo;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RevoLanguage implements ScriptLanguage {

    @Override
    public String name() {
        return "revo";
    }

    @Override
    public List<String> extensions() {
        return List.of("rv");
    }

    @Override
    public ScriptEngine start(Host host) {
        return new RevoEngine(host);
    }

    @Override
    public void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {
        Path directory = place.resolve(".moud");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("moud.d.rv"), RevoTypes.declare(api, classes));
    }
}
