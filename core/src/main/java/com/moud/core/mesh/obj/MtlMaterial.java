package com.moud.core.mesh.obj;

public record MtlMaterial(
        String name,
        float colorR,
        float colorG,
        float colorB,
        float alpha,
        String diffuseTexture
) {
    public static MtlMaterial defaults(String name) {
        return new MtlMaterial(name, 1f, 1f, 1f, 1f, null);
    }
}
