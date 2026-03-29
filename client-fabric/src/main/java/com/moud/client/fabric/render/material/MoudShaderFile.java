package com.moud.client.fabric.render.material;

import com.moud.core.assets.AssetHash;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public final class MoudShaderFile {
    private final Int2ObjectMap<String> stageSources;
    private final Map<String, MoudShaderUniform> uniformsByName;
    private final List<MoudShaderUniform> uniforms;
    private final List<MoudShaderUniform> exposedUniforms;
    private final AssetHash programHash;
    private final List<String> dependencies;

    public MoudShaderFile(Int2ObjectMap<String> stageSources, List<MoudShaderUniform> uniforms) {
        this(stageSources, uniforms, null, List.of());
    }

    public MoudShaderFile(Int2ObjectMap<String> stageSources,
                          List<MoudShaderUniform> uniforms,
                          AssetHash programHash,
                          List<String> dependencies) {
        Objects.requireNonNull(stageSources, "stageSources");
        Objects.requireNonNull(uniforms, "uniforms");

        this.stageSources = new Int2ObjectArrayMap<>(stageSources);
        this.uniforms = List.copyOf(uniforms);
        this.programHash = programHash;
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);

        HashMap<String, MoudShaderUniform> byName = new HashMap<>();
        ArrayList<MoudShaderUniform> exposed = new ArrayList<>();
        for (MoudShaderUniform u : uniforms) {
            if (u == null || u.name() == null) {
                continue;
            }
            byName.putIfAbsent(u.name(), u);
            if (u.exposed()) {
                exposed.add(u);
            }
        }
        this.uniformsByName = Map.copyOf(byName);
        this.exposedUniforms = List.copyOf(exposed);
    }

    public Int2ObjectMap<String> stageSources() {
        return stageSources;
    }

    public List<MoudShaderUniform> uniforms() {
        return uniforms;
    }

    public List<MoudShaderUniform> exposedUniforms() {
        return exposedUniforms;
    }

    public MoudShaderUniform uniform(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return uniformsByName.get(name);
    }

    public AssetHash programHash() {
        return programHash;
    }

    public List<String> dependencies() {
        return dependencies;
    }
}
