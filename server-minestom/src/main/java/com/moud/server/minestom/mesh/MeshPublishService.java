package com.moud.server.minestom.mesh;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.io.MeshBinaryFormat;
import com.moud.core.mesh.source.ArrayMeshResolver;
import com.moud.core.mesh.source.GeneratorMesh;
import com.moud.core.mesh.source.MeshAuthority;
import com.moud.core.mesh.source.MeshSource;
import com.moud.core.mesh.source.MeshSourceCodec;
import com.moud.core.mesh.source.ObjRefMesh;
import com.moud.core.scene.Node;
import com.moud.net.protocol.MeshGeneratorPublish;
import com.moud.net.protocol.MeshPublish;
import com.moud.net.protocol.Message;
import com.moud.server.minestom.util.DebugLog;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MeshPublishService {
    private static final int CHUNK_PAYLOAD_BYTES = 32 * 1024;

    private final ArrayMeshResolver resolver;
    private final List<Message> pending = new ArrayList<>();
    private final Map<Long, byte[]> nodeHashes = new LinkedHashMap<>();
    private final Map<Long, List<Message>> latestByNode = new LinkedHashMap<>();

    public MeshPublishService(ArrayMeshResolver resolver) {
        this.resolver = resolver;
    }

    public synchronized void register(Node node) {
        if (node == null) {
            return;
        }
        String raw = node.getProperty("mesh_source");
        MeshSource fromModelPath = sourceFromModelPath(node);
        if ((raw == null || raw.isBlank()) && fromModelPath == null) {
            unregister(node.nodeId());
            return;
        }
        if ((raw == null || raw.isBlank()) && fromModelPath != null) {
            var resolved = resolver.resolve(fromModelPath);
            if (resolved.isEmpty()) return;
            publishServerAuthMesh(node.nodeId(), resolved.get());
            return;
        }
        MeshSource source;
        try {
            source = MeshSourceCodec.decode(raw);
        } catch (RuntimeException e) {
            DebugLog.warn("mesh", "mesh_source decode failed for node " + node.nodeId() + ": " + e.getMessage());
            return;
        }
        if (source instanceof GeneratorMesh gm && gm.authority() == MeshAuthority.CLIENT) {
            var msg = new MeshGeneratorPublish(
                    node.nodeId(),
                    gm.scriptPath(),
                    gm.params().toString(),
                    gm.seed());
            pending.add(msg);
            latestByNode.put(node.nodeId(), List.of(msg));
            return;
        }
        var resolved = resolver.resolve(source);
        if (resolved.isEmpty()) {
            return;
        }
        publishServerAuthMesh(node.nodeId(), resolved.get());
    }

    private static MeshSource sourceFromModelPath(Node node) {
        String modelPath = node.getProperty("model_path");
        if (modelPath == null || modelPath.isBlank()) return null;
        String lower = modelPath.toLowerCase();
        if (lower.endsWith(".obj")) return new ObjRefMesh(modelPath);
        return null;
    }

    public synchronized void unregister(long nodeId) {
        nodeHashes.remove(nodeId);
        latestByNode.remove(nodeId);
    }

    public synchronized List<Message> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        var out = List.copyOf(pending);
        pending.clear();
        return out;
    }

    public synchronized List<Message> getLatest() {
        if (latestByNode.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<Message>(latestByNode.size() * 2);
        for (var msgs : latestByNode.values()) {
            out.addAll(msgs);
        }
        return List.copyOf(out);
    }

    private void publishServerAuthMesh(long nodeId, ArrayMesh mesh) {
        byte[] hash = HexFormat.of().parseHex(mesh.hash());
        byte[] previous = nodeHashes.put(nodeId, hash);
        if (previous != null && java.util.Arrays.equals(previous, hash)) {
            return;
        }
        byte[] body = MeshBinaryFormat.write(mesh);
        int chunks = Math.max(1, (int) Math.ceil(body.length / (double) CHUNK_PAYLOAD_BYTES));
        var nodeMessages = new ArrayList<Message>(chunks);
        for (int i = 0; i < chunks; i++) {
            int start = i * CHUNK_PAYLOAD_BYTES;
            int end = Math.min(body.length, start + CHUNK_PAYLOAD_BYTES);
            byte[] slice = new byte[end - start];
            System.arraycopy(body, start, slice, 0, slice.length);
            var msg = new MeshPublish(nodeId, hash, i, chunks, slice);
            pending.add(msg);
            nodeMessages.add(msg);
        }
        latestByNode.put(nodeId, List.copyOf(nodeMessages));
    }
}
