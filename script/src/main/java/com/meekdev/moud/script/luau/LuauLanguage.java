package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public final class LuauLanguage implements ScriptLanguage {

    private Path place;
    private String types;
    private Instance world;
    private Predicate<Instance> keep;
    private String scene = "";

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
        if (!place.equals(this.place)) world = null;
        this.place = place;
        types = LuauTypes.declare(api, classes);
        scene = world == null ? "" : LuauScene.declare(types, world, keep);
        Path file = types(place);
        writeIfChanged(file, types + scene);
        writeIfChanged(directory.resolve("plugin.luau"), LuauResource.text("project/plugin.luau"));
        LuauProject.write(place, file);
    }

    @Override
    public void writeScene(Path place, Instance world, Predicate<Instance> keep) throws IOException {
        if (!place.equals(this.place)) types = null;
        this.place = place;
        this.world = world;
        this.keep = keep;
        if (types == null) return;
        String next = LuauScene.declare(types, world, keep);
        if (next.equals(scene)) return;
        scene = next;
        writeIfChanged(types(place), types + scene);
    }

    private static Path types(Path place) {
        return place.resolve(".moud").resolve("types.d.luau");
    }

    private static void writeIfChanged(Path file, String text) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).equals(text)) return;
        Files.writeString(file, text);
    }
}
