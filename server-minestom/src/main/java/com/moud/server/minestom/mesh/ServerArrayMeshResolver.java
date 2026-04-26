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
import com.moud.server.minestom.assets.AssetStore;
import com.moud.server.minestom.util.DebugLog;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public final class ServerArrayMeshResolver implements ArrayMeshResolver {
    private final AssetStore assets;
    private volatile Function<GeneratorMesh, Optional<ArrayMesh>> generatorRunner;

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
        };
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
