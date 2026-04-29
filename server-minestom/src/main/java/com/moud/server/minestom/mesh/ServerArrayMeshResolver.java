package com.moud.server.minestom.mesh;

import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.io.MeshBinaryFormat;
import com.moud.core.mesh.source.ArrayMeshResolver;
import com.moud.core.mesh.source.AssetRefMesh;
import com.moud.core.mesh.source.GeneratorMesh;
import com.moud.core.mesh.source.HashRefMesh;
import com.moud.core.mesh.source.InlineMesh;
import com.moud.core.mesh.source.MeshSource;
import com.moud.core.mesh.source.ObjRefMesh;
import com.moud.core.mesh.obj.MtlMaterial;
import com.moud.core.mesh.obj.MtlParser;
import com.moud.core.mesh.obj.ObjMeshBuilder;
import com.moud.core.mesh.obj.ObjModel;
import com.moud.core.mesh.obj.ObjParser;
import com.moud.server.minestom.assets.AssetStore;
import com.moud.server.minestom.util.DebugLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ServerArrayMeshResolver implements ArrayMeshResolver {
    private final AssetStore assets;
    private volatile Function<GeneratorMesh, Optional<ArrayMesh>> generatorRunner;
    private final java.util.Map<String, ArrayMesh> objCacheByAssetHash = new java.util.concurrent.ConcurrentHashMap<>();

    public ServerArrayMeshResolver(AssetStore assets) {
        this.assets = assets;
    }

    public void setGeneratorRunner(Function<GeneratorMesh, Optional<ArrayMesh>> runner) {
        this.generatorRunner = runner;
    }

    @Override
    public Optional<ArrayMesh> resolve(MeshSource source) {
        return switch (source) {
            case null -> Optional.empty();
            case InlineMesh im -> resolveInline(im);
            case AssetRefMesh ar -> resolveAsset(ar);
            case HashRefMesh hr -> MeshRegistry.instance().get(hr.hash());
            case GeneratorMesh gm -> resolveGenerator(gm);
            case ObjRefMesh om -> resolveObj(om);
        };
    }

    private Optional<ArrayMesh> resolveObj(ObjRefMesh om) {
        if (assets == null) return Optional.empty();
        try {
            ResPath objPath = ResPath.of(om.path());
            AssetMeta objMeta = assets.meta(objPath);
            if (objMeta == null) return Optional.empty();
            String cacheKey = objMeta.hash().hex();
            ArrayMesh hit = objCacheByAssetHash.get(cacheKey);
            if (hit != null) {
                return Optional.of(hit);
            }
            byte[] objBytes = assets.readBlob(objMeta.hash());
            String objText = new String(objBytes, StandardCharsets.UTF_8);
            ObjModel model = ObjParser.parse(objText);

            Map<String, MtlMaterial> materials = Map.of();
            String mtlLib = model.mtlLibName();
            if (mtlLib != null && !mtlLib.isBlank()) {
                ResPath mtlPath = resolveSibling(objPath, mtlLib);
                AssetMeta mtlMeta = assets.meta(mtlPath);
                if (mtlMeta != null) {
                    byte[] mtlBytes = assets.readBlob(mtlMeta.hash());
                    String mtlText = new String(mtlBytes, StandardCharsets.UTF_8);
                    materials = MtlParser.parse(mtlText);
                }
            }

            ArrayMesh mesh = ObjMeshBuilder.build(model, materials);
            var cached = MeshRegistry.instance().get(mesh.hash());
            ArrayMesh registered = cached.orElseGet(() -> MeshRegistry.instance().register(mesh));
            objCacheByAssetHash.put(cacheKey, registered);
            return Optional.of(registered);
        } catch (IOException | RuntimeException e) {
            DebugLog.warn("mesh", "obj mesh load failed for " + om.path() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static ResPath resolveSibling(ResPath base, String name) {
        String p = base.path();
        int slash = p.lastIndexOf('/');
        String dir = slash >= 0 ? p.substring(0, slash + 1) : "";
        return ResPath.of(ResPath.SCHEME + dir + name);
    }

    private Optional<ArrayMesh> resolveInline(InlineMesh im) {
        var cached = MeshRegistry.instance().get(im.hash());
        if (cached.isPresent()) {
            return cached;
        }
        try {
            var mesh = MeshBinaryFormat.read(im.meshBinary());
            return Optional.of(MeshRegistry.instance().register(mesh));
        } catch (RuntimeException e) {
            DebugLog.warn("mesh", "inline mesh decode failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ArrayMesh> resolveAsset(AssetRefMesh ar) {
        if (assets == null) {
            return Optional.empty();
        }
        if (ar.path() != null && ar.path().toLowerCase().endsWith(".obj")) {
            return resolveObj(new ObjRefMesh(ar.path()));
        }
        try {
            ResPath path = ResPath.of(ar.path());
            AssetMeta meta = assets.meta(path);
            if (meta == null) {
                return Optional.empty();
            }
            byte[] blob = assets.readBlob(meta.hash());
            var mesh = MeshBinaryFormat.read(blob);
            var cached = MeshRegistry.instance().get(mesh.hash());
            if (cached.isPresent()) {
                return cached;
            }
            return Optional.of(MeshRegistry.instance().register(mesh));
        } catch (IOException | RuntimeException e) {
            DebugLog.warn("mesh", "asset mesh load failed for " + ar.path() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ArrayMesh> resolveGenerator(GeneratorMesh gm) {
        var runner = generatorRunner;
        if (runner == null) {
            return Optional.empty();
        }
        return runner.apply(gm);
    }
}
