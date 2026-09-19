package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LuauLanguage implements ScriptLanguage {

    @Override
    public String name() {
        return "luau";
    }

    @Override
    public List<String> extensions() {
        return List.of("luau");
    }

    @Override
    public ScriptEngine start(Host host) {
        return new LuauEngine(host);
    }

    @Override
    public void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {
        Path directory = place.resolve(".moud");
        Files.createDirectories(directory);
        Path types = directory.resolve("types.d.luau");
        Files.writeString(types, LuauTypes.declare(api, classes));
        Files.writeString(directory.resolve("plugin.luau"), LuauResource.text("project/plugin.luau"));
        LuauProject.write(place, types);
    }
}
