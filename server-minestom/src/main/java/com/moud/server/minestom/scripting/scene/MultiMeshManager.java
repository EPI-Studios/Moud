package com.moud.server.minestom.scripting.scene;

import com.moud.net.protocol.MultiMeshData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MultiMeshManager {
    private final HashMap<Long, float[]> pendingMultiMesh = new HashMap<>();
    private final HashMap<Long, float[]> latestMultiMesh = new HashMap<>();

    public void setInstances(long nodeId, float[] data) {
        if (nodeId <= 0L || data == null) {
            return;
        }
        pendingMultiMesh.put(nodeId, data);
        latestMultiMesh.put(nodeId, data);
    }

    public void removeNode(long nodeId) {
        boolean hadMultiMesh = pendingMultiMesh.remove(nodeId) != null;
        hadMultiMesh |= latestMultiMesh.remove(nodeId) != null;
        if (hadMultiMesh) {
            pendingMultiMesh.put(nodeId, new float[0]);
        }
    }

    public List<MultiMeshData> getLatestMultiMesh() {
        if (latestMultiMesh.isEmpty()) {
            return List.of();
        }
        List<MultiMeshData> out = new ArrayList<>(latestMultiMesh.size());
        for (Map.Entry<Long, float[]> e : latestMultiMesh.entrySet()) {
            float[] v = e.getValue();
            out.add(new MultiMeshData(e.getKey(), 0, v == null ? 0 : v.length, v));
        }
        return out;
    }

    public List<MultiMeshData> drainMultiMesh() {
        if (pendingMultiMesh.isEmpty()) {
            return List.of();
        }
        List<MultiMeshData> out = new ArrayList<>(pendingMultiMesh.size());
        for (Map.Entry<Long, float[]> e : pendingMultiMesh.entrySet()) {
            float[] v = e.getValue();
            out.add(new MultiMeshData(e.getKey(), 0, v == null ? 0 : v.length, v));
        }
        pendingMultiMesh.clear();
        return out;
    }
}
