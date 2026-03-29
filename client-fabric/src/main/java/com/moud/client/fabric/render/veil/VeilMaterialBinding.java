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
import java.nio.charset.StandardCharsets;
import net.minecraft.util.Identifier;

public final class VeilMaterialBinding {
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
        if (program == null || material == null || shaderFile == null) {
            return;
        }
        program.clearSamplers();
        for (Map.Entry<String, MoudMaterial.Param> e : material.params().entrySet()) {
            String name = e.getKey();
            MoudMaterial.Param param = e.getValue();
            if (name == null || name.isBlank() || param == null) {
                continue;
            }
            MoudShaderUniform u = shaderFile.uniform(name);
            if (param instanceof MoudMaterial.Param.Texture tex) {
                if (u != null && u.isSampler()) {
                    Identifier id = MoudTextures.resolve(tex.textureRef());
                    program.setSampler(name, id);
                }
                continue;
            }
            if (param instanceof MoudMaterial.Param.StringParam s) {
                if (u != null && u.isSampler()) {
                    Identifier id = MoudTextures.resolve(s.value());
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
                if (type.startsWith("int") || type.startsWith("uint") || type.startsWith("ivec")) {
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

    private void ensureMaterialLoaded() {
        if (materialPath == null) {
            if (shaderPath != null) {
                material = new MoudMaterial(shaderPath, Map.of());
            }
            return;
        }

        String txt = MoudTextAssets.readText(materialPath);
        if (txt == null) {
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
            return;
        }
        boolean sameRoot = Objects.equals(cachedShaderText, txt);
        if (sameRoot && shaderFile != null && programId != null && !dependenciesChanged()) {
            return;
        }
        cachedShaderText = txt;
        shaderFile = MoudShaderParser.parse(txt, sp);
        programId = null;
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
}
