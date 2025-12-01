package com.moud.server.editor.runtime;

public final class SceneRuntimeFactory {
    private SceneRuntimeFactory() {}

    public static SceneRuntimeAdapter create(String sceneId, String objectType) {
        String normalized = objectType == null ? "" : objectType.toLowerCase();
        return switch (normalized) {
            case "model" -> new ModelRuntimeAdapter(sceneId);
            case "display" -> new DisplayRuntimeAdapter(sceneId);
            case "light" -> new LightRuntimeAdapter(sceneId);
            case "terrain" -> new TerrainRuntimeAdapter(sceneId);
            case "player_model" -> new PlayerModelRuntimeAdapter(sceneId);
            case "camera" -> new CameraRuntimeAdapter();
            case "particle_emitter" -> new ParticleEmitterRuntimeAdapter();
            default -> null;
        };
    }
}
