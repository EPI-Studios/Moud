package com.moud.core.mesh.source;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Base64;
import java.util.Locale;

public final class MeshSourceCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MeshSourceCodec() {
    }

    public static MeshSource decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var root = JsonParser.parseString(raw).getAsJsonObject();
        var kind = root.get("kind").getAsString();
        return switch (kind) {
            case "inline" -> new InlineMesh(
                    root.get("hash").getAsString(),
                    Base64.getDecoder().decode(root.get("b64").getAsString()));
            case "asset" -> new AssetRefMesh(root.get("path").getAsString());
            case "hash" -> new HashRefMesh(root.get("hash").getAsString());
            case "gen" -> {
                JsonObject params = root.has("params") && root.get("params").isJsonObject()
                        ? root.getAsJsonObject("params")
                        : new JsonObject();
                long seed = root.has("seed") ? root.get("seed").getAsLong() : 0L;
                MeshAuthority authority = root.has("authority")
                        ? MeshAuthority.parse(root.get("authority").getAsString(), MeshAuthority.SERVER)
                        : MeshAuthority.SERVER;
                yield new GeneratorMesh(root.get("script").getAsString(), params, seed, authority);
            }
            default -> throw new IllegalArgumentException("unknown mesh source kind: " + kind);
        };
    }

    public static String encode(MeshSource source) {
        if (source == null) {
            return "";
        }
        var root = new JsonObject();
        switch (source) {
            case InlineMesh im -> {
                root.addProperty("kind", "inline");
                root.addProperty("hash", im.hash());
                root.addProperty("b64", Base64.getEncoder().encodeToString(im.meshBinary()));
            }
            case AssetRefMesh ar -> {
                root.addProperty("kind", "asset");
                root.addProperty("path", ar.path());
            }
            case HashRefMesh hr -> {
                root.addProperty("kind", "hash");
                root.addProperty("hash", hr.hash());
            }
            case GeneratorMesh gm -> {
                root.addProperty("kind", "gen");
                root.addProperty("script", gm.scriptPath());
                root.add("params", gm.params());
                root.addProperty("seed", gm.seed());
                root.addProperty("authority", gm.authority().name().toLowerCase(Locale.ROOT));
            }
        }
        return GSON.toJson(root);
    }
}
