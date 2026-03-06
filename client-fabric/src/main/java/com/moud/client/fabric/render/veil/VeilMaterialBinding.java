package com.moud.client.fabric.render.veil;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.Identifier;

public final class VeilMaterialBinding {
    private String activeKey;
    private String materialPath;
    private String shaderPath;

    private String cachedMaterialText;
    private String cachedShaderText;

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
        if (Objects.equals(cachedShaderText, txt) && shaderFile != null && programId != null) {
            return;
        }
        cachedShaderText = txt;
        shaderFile = MoudShaderParser.parse(txt);
        programId = null;
        if (shaderFile != null) {
            AssetHash hash = AssetHash.sha256(txt.getBytes(StandardCharsets.UTF_8));
            programId = Identifier.of("moud", "dyn/" + hash.hex());
        }
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim();
    }
}

