package com.moud.client.fabric.render.veil;

import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.material.MoudMaterial;
import com.moud.client.fabric.render.material.MoudMaterialParser;
import com.moud.client.fabric.render.material.MoudShaderFile;
import com.moud.client.fabric.render.material.MoudShaderParser;
import com.moud.client.fabric.render.material.MoudShaderUniform;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.TextureManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VeilMaterialBinding {
    private static final Logger LOGGER = LoggerFactory.getLogger(VeilMaterialBinding.class);
    private static final Set<String> LOGGED_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final String[] BUILTIN_PBR_SAMPLERS = {
            "albedo_texture",
            "normal_texture",
            "roughness_texture",
            "metallic_texture",
            "emission_texture",
            "ao_texture",
            "orm_texture",
            "heightmap_texture"
    };
    private static final float[] DEFAULT_ALBEDO_COLOR = {1.0f, 1.0f, 1.0f, 1.0f};

    private String activeKey;
    private String materialPath;
    private String shaderPath;

    private String cachedMaterialText;
    private String cachedShaderText;
    private String[] cachedShaderDeps = new String[0];
    private long[] cachedShaderDepVersions = new long[0];

    private MoudMaterial material;
    private MoudShaderFile shaderFile;
    private Identifier programId;

    public boolean configure(String materialPath, String shaderPath) {
        String mp = norm(materialPath);
        String sp = norm(shaderPath);
        String key = !mp.isEmpty() ? "mat:" + mp : (!sp.isEmpty() ? "shader:" + sp : "");
        if (key.isEmpty()) {
            clear();
            return false;
        }
        if (!Objects.equals(activeKey, key)) {
            clear();
            activeKey = key;
            this.materialPath = mp.isEmpty() ? null : mp;
            this.shaderPath = sp.isEmpty() ? null : sp;
        }
        return true;
    }

    public ShaderProgram resolveProgram() {
        ensureMaterialLoaded();
        if (material == null) {
            return null;
        }
        ensureShaderLoaded();
        if (shaderFile == null || programId == null) {
            return null;
        }
        return VeilDynamicShaders.getOrCompile(programId, shaderFile.stageSources());
    }

    public void applyMaterial(ShaderProgram program) {
        if (program == null || material == null) {
            return;
        }
        program.clearSamplers();
        for (String sampler : BUILTIN_PBR_SAMPLERS) {
            program.setSampler(sampler, MoudTextures.defaultSamplerFor(sampler));
        }
        setDefaultUniform(program, "albedo_color", DEFAULT_ALBEDO_COLOR);
        setDefaultUniform(program, "metallic", 0.0f);
        setDefaultUniform(program, "roughness", 1.0f);
        setDefaultUniform(program, "specular", 0.5f);
        setDefaultUniform(program, "ambient_light", 1.0f);
        setDefaultUniform(program, "heightmap_scale", 0.0f);
        setDefaultUniform(program, "normal_scale", 1.0f);
        setDefaultUniform(program, "roughness_texture_channel", 0);
        setDefaultUniform(program, "normal_enabled", 0);
        setDefaultUniform(program, "emission_enabled", 0);
        setDefaultUniform(program, "ao_enabled", 0);
        setDefaultUniform(program, "heightmap_enabled", 0);
        setDefaultUniform(program, "heightmap_deep_parallax", 0);
        setDefaultUniform(program, "heightmap_min_layers", 8);
        setDefaultUniform(program, "heightmap_max_layers", 32);
        setDefaultUniform(program, "orm_enabled", 0);
        setDefaultUniform(program, "UvScale", new float[]{1.0f, 1.0f});
        setDefaultUniform(program, "UvOffset", new float[]{0.0f, 0.0f});
        for (Map.Entry<String, MoudMaterial.Param> e : material.params().entrySet()) {
            String name = e.getKey();
            MoudMaterial.Param param = e.getValue();
            if (name == null || name.isBlank() || param == null) {
                continue;
            }
            MoudShaderUniform u = shaderFile != null ? shaderFile.uniform(name) : null;
            if (param instanceof MoudMaterial.Param.Texture tex) {
                if (u == null || u.isSampler()) {
                    Identifier id = resolveSampler(name, tex.textureRef());
                    program.setSampler(name, id);
                }
                continue;
            }
            if (param instanceof MoudMaterial.Param.StringParam s) {
                if (u == null || u.isSampler()) {
                    Identifier id = resolveSampler(name, s.value());
                    program.setSampler(name, id);
                }
                continue;
            }

            ShaderUniformAccess ua = program.getUniformSafe(name);
            if (ua == null || !ua.isValid()) {
                continue;
            }

            String type = u != null ? u.glslType().toLowerCase(Locale.ROOT) : "";
            if (param instanceof MoudMaterial.Param.Bool b) {
                ua.setInt(b.value() ? 1 : 0);
            } else if (param instanceof MoudMaterial.Param.Number num) {
                if (isIntegerUniform(name, type, num.value())) {
                    ua.setInt(Math.round(num.value()));
                } else {
                    ua.setFloat(num.value());
                }
            } else if (param instanceof MoudMaterial.Param.Vec vec) {
                float[] v = vec.values();
                if (v == null || v.length == 0) {
                    continue;
                }
                if (type.startsWith("ivec")) {
                    int[] vi = new int[v.length];
                    for (int i = 0; i < v.length; i++) {
                        vi[i] = Math.round(v[i]);
                    }
                    ua.setVectorI(vi);
                } else {
                    ua.setVector(v);
                }
            }
        }

        if (hasParam("uv_scale")) {
            MoudMaterial.Param p = material.params().get("uv_scale");
            if (p instanceof MoudMaterial.Param.Vec v && v.values().length >= 2) {
                setDefaultUniform(program, "UvScale", new float[]{v.values()[0], v.values()[1]});
            }
        }
        if (hasParam("uv_offset")) {
            MoudMaterial.Param p = material.params().get("uv_offset");
            if (p instanceof MoudMaterial.Param.Vec v && v.values().length >= 2) {
                setDefaultUniform(program, "UvOffset", new float[]{v.values()[0], v.values()[1]});
            }
        }

        if (!hasParam("normal_enabled") && hasTextureParam("normal_texture")) {
            setDefaultUniform(program, "normal_enabled", 1);
        }
        if (!hasParam("emission_enabled") && hasTextureParam("emission_texture")) {
            setDefaultUniform(program, "emission_enabled", 1);
        }
        if (!hasParam("ao_enabled") && hasTextureParam("ao_texture")) {
            setDefaultUniform(program, "ao_enabled", 1);
        }
        if (!hasParam("heightmap_enabled") && hasTextureParam("heightmap_texture")) {
            setDefaultUniform(program, "heightmap_enabled", 1);
        }
        if (!hasParam("orm_enabled") && shouldInferOrmEnabled()) {
            setDefaultUniform(program, "orm_enabled", 1);
        }
    }

    public Identifier programId() {
        return programId;
    }

    public MoudMaterial material() {
        return material;
    }

    public MoudShaderFile shaderFile() {
        return shaderFile;
    }

    public boolean hasTextureParam(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        ensureMaterialLoaded();
        if (material == null || material.params() == null) {
            return false;
        }
        MoudMaterial.Param param = material.params().get(name);
        return param instanceof MoudMaterial.Param.Texture || param instanceof MoudMaterial.Param.StringParam;
    }

    public boolean hasParam(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        ensureMaterialLoaded();
        return material != null && material.params() != null && material.params().containsKey(name);
    }

    public String materialPath() {
        return materialPath;
    }

    public String shaderPath() {
        return shaderPath;
    }

    public void clear() {
        activeKey = null;
        materialPath = null;
        shaderPath = null;
        cachedMaterialText = null;
        cachedShaderText = null;
        material = null;
        shaderFile = null;
        programId = null;
    }

    private static void warnOnce(String key, String message) {
        if (key == null || message == null) {
            return;
        }
        if (LOGGED_WARNINGS.add(key)) {
            LOGGER.warn(message);
        }
    }

    private void ensureMaterialLoaded() {
        if (materialPath == null) {
            if (shaderPath != null) {
                material = new MoudMaterial(shaderPath, Map.of());
            }
            return;
        }

        String txt = MoudTextAssets.readText(materialPath);
        if (txt == null) {
            if (!MoudTextAssets.exists(materialPath) && MoudTextAssets.textAssetPaths().size() > 0) {
                warnOnce("material-missing:" + materialPath, "[Moud] Material asset unavailable path=" + materialPath);
            }
            return;
        }
        if (Objects.equals(cachedMaterialText, txt) && material != null) {
            return;
        }
        cachedMaterialText = txt;
        material = MoudMaterialParser.parse(txt);
        if (material != null) {
            shaderPath = norm(material.shader());
        } else {
            warnOnce("material-parse:" + materialPath, "[Moud] Material parse failed path=" + materialPath);
            shaderPath = null;
        }
        cachedShaderText = null;
        shaderFile = null;
        programId = null;
    }

    private void ensureShaderLoaded() {
        String sp = shaderPath;
        if (sp == null || sp.isEmpty()) {
            return;
        }
        if (!sp.startsWith(ResPath.SCHEME)) {
            return;
        }
        String txt = MoudTextAssets.readText(sp);
        if (txt == null) {
            if (!MoudTextAssets.exists(sp)) {
                warnOnce("shader-missing:" + sp, "[Moud] Shader asset unavailable path=" + sp
                        + (materialPath != null && !materialPath.isBlank() ? " material=" + materialPath : ""));
            }
            return;
        }
        boolean sameRoot = Objects.equals(cachedShaderText, txt);
        if (sameRoot && shaderFile != null && programId != null && !dependenciesChanged()) {
            return;
        }
        cachedShaderText = txt;
        shaderFile = MoudShaderParser.parse(txt, sp);
        programId = null;
        if (shaderFile == null) {
            warnOnce("shader-parse:" + sp, "[Moud] Shader parse failed path=" + sp
                    + (materialPath != null && !materialPath.isBlank() ? " material=" + materialPath : ""));
        }
        if (shaderFile != null) {
            AssetHash hash = shaderFile.programHash();
            if (hash == null) {
                hash = AssetHash.sha256(txt.getBytes(StandardCharsets.UTF_8));
            }
            programId = Identifier.of("moud", "dyn/" + hash.hex());
            snapshotDependencyVersions(shaderFile);
        }
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim();
    }

    private boolean dependenciesChanged() {
        MoudShaderFile sf = shaderFile;
        if (sf == null) {
            return false;
        }
        List<String> deps = sf.dependencies();
        if (deps == null || deps.isEmpty()) {
            return false;
        }
        if (cachedShaderDeps.length != deps.size() || cachedShaderDepVersions.length != deps.size()) {
            return true;
        }
        for (int i = 0; i < deps.size(); i++) {
            String path = deps.get(i);
            if (!Objects.equals(path, cachedShaderDeps[i])) {
                return true;
            }
            long v = MoudTextAssets.versionOf(path);
            if (v != cachedShaderDepVersions[i]) {
                return true;
            }
        }
        return false;
    }

    private void snapshotDependencyVersions(MoudShaderFile sf) {
        List<String> deps = sf == null ? null : sf.dependencies();
        if (deps == null || deps.isEmpty()) {
            cachedShaderDeps = new String[0];
            cachedShaderDepVersions = new long[0];
            return;
        }
        cachedShaderDeps = deps.toArray(String[]::new);
        cachedShaderDepVersions = new long[deps.size()];
        for (int i = 0; i < deps.size(); i++) {
            cachedShaderDepVersions[i] = MoudTextAssets.versionOf(deps.get(i));
        }
    }

    private static void setDefaultUniform(ShaderProgram program, String name, float value) {
        ShaderUniformAccess ua = program.getUniformSafe(name);
        if (ua != null && ua.isValid()) {
            ua.setFloat(value);
        }
    }

    private static void setDefaultUniform(ShaderProgram program, String name, int value) {
        ShaderUniformAccess ua = program.getUniformSafe(name);
        if (ua != null && ua.isValid()) {
            ua.setInt(value);
        }
    }

    private static void setDefaultUniform(ShaderProgram program, String name, float[] value) {
        if (value == null || value.length == 0) {
            return;
        }
        ShaderUniformAccess ua = program.getUniformSafe(name);
        if (ua != null && ua.isValid()) {
            ua.setVector(value);
        }
    }

    private Identifier resolveSampler(String samplerName, String textureRef) {
        Identifier resolved = MoudTextures.resolve(textureRef);
        if (resolved == null || TextureManager.MISSING_IDENTIFIER.equals(resolved)) {
            return MoudTextures.defaultSamplerFor(samplerName);
        }
        return resolved;
    }

    private boolean shouldInferOrmEnabled() {
        return hasTextureParam("orm_texture")
                && !hasTextureParam("roughness_texture")
                && !hasTextureParam("metallic_texture")
                && !hasTextureParam("ao_texture");
    }

    private static boolean isIntegerUniform(String name, String type, float value) {
        if (type.startsWith("int") || type.startsWith("uint") || type.startsWith("ivec")) {
            return true;
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        return switch (name) {
            case "roughness_texture_channel",
                    "heightmap_min_layers",
                    "heightmap_max_layers",
                    "heightmap_deep_parallax",
                    "heightmap_enabled",
                    "normal_enabled",
                    "emission_enabled",
                    "ao_enabled",
                    "orm_enabled" -> true;
            default -> Math.abs(value - Math.round(value)) <= 1.0e-6f && name.endsWith("_enabled");
        };
    }
}
