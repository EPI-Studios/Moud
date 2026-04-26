package com.moud.client.fabric.render.mesh.cache;

import com.moud.client.fabric.render.mesh.upload.ProceduralMeshUploader;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.io.MeshBinaryFormat;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.MeshPublish;

import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MeshPublishReassembler {
    private static final Map<String, PendingMesh> PENDING = new ConcurrentHashMap<>();

    private MeshPublishReassembler() {
    }

    public static void onPublish(MeshPublish publish) {
        String hex = HexFormat.of().formatHex(publish.hash());
        if (publish.chunkCount() <= 0) {
            bindExisting(publish.nodeId(), hex);
            return;
        }
        PendingMesh pending = PENDING.computeIfAbsent(hex, h -> new PendingMesh(publish.chunkCount()));
        synchronized (pending) {
            if (pending.complete) {
                ClientMeshBindings.bind(publish.nodeId(), hex);
                return;
            }
            if (publish.chunkIndex() < 0 || publish.chunkIndex() >= pending.chunks.length) {
                ClientDebugLog.warn("mesh", "mesh publish chunk index " + publish.chunkIndex() + " out of range for hash " + hex);
                return;
            }
            if (pending.chunks[publish.chunkIndex()] == null) {
                pending.chunks[publish.chunkIndex()] = publish.payload();
                pending.received++;
            }
            pending.targetNodes.add(publish.nodeId());
            if (pending.received < pending.chunks.length) {
                return;
            }
            pending.complete = true;
        }
        finalizePending(hex, pending);
    }

    private static void bindExisting(long nodeId, String hex) {
        if (MeshRegistry.instance().contains(hex) || ClientMeshCache.contains(hex)) {
            ClientMeshBindings.bind(nodeId, hex);
        } else {
        }
    }

    private static void finalizePending(String hex, PendingMesh pending) {
        byte[] joined = join(pending.chunks);
        try {
            ArrayMesh mesh = MeshBinaryFormat.read(joined);
            if (!mesh.hash().equals(hex)) {
                ClientDebugLog.warn("mesh", "mesh hash mismatch: published=" + hex + " decoded=" + mesh.hash());
            }
            MeshRegistry.instance().register(mesh);
            ProceduralMeshUploader.enqueue(mesh);
            for (long nodeId : pending.targetNodes) {
                ClientMeshBindings.bind(nodeId, mesh.hash());
            }
        } catch (RuntimeException e) {
            ClientDebugLog.warn("mesh", "mesh decode failed for hash " + hex + ": " + e.getMessage());
        } finally {
            PENDING.remove(hex);
        }
    }

    private static byte[] join(byte[][] chunks) {
        int total = 0;
        for (byte[] c : chunks) {
            total += c == null ? 0 : c.length;
        }
        byte[] out = new byte[total];
        int cursor = 0;
        for (byte[] c : chunks) {
            if (c == null) continue;
            System.arraycopy(c, 0, out, cursor, c.length);
            cursor += c.length;
        }
        return out;
    }

    public static void clear() {
        PENDING.clear();
    }

    private static final class PendingMesh {
        final byte[][] chunks;
        int received;
        boolean complete;
        final Set<Long> targetNodes = ConcurrentHashMap.newKeySet();

        PendingMesh(int chunkCount) {
            this.chunks = new byte[Math.max(1, chunkCount)][];
        }
    }
}
