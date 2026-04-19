package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.scene.ClientLocalNodes;
import com.moud.client.fabric.scene.ClientPropertyOverrides;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.SceneNodeTransforms;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3f;

public final class NodeApi {

    private final long nodeId;
    private NetApi netApi;

    public NodeApi(long nodeId) {
        this.nodeId = nodeId;
    }

    public void attachNet(NetApi netApi) {
        this.netApi = netApi;
    }

    public NetApi net() {
        return netApi;
    }

    public long getId() {
        return nodeId;
    }

    public String getType() {
        SceneSnapshot.NodeSnapshot n = findSelf();
        return n != null ? n.type() : "";
    }

    public String getName() {
        SceneSnapshot.NodeSnapshot n = findSelf();
        return n != null && n.name() != null ? n.name() : "";
    }

    public String getProperty(String key) {
        SceneSnapshot.NodeSnapshot n = findSelf();
        if (n == null || key == null) return "";
        List<SceneSnapshot.Property> props = n.properties();
        if (props == null) return "";
        for (SceneSnapshot.Property p : props) {
            if (key.equals(p.key())) return p.value() != null ? p.value() : "";
        }
        return "";
    }

    public float getX() {
        return ParseUtils.parseFloat(getProperty("pos_x"), 0f);
    }

    public float getY() {
        return ParseUtils.parseFloat(getProperty("pos_y"), 0f);
    }

    public float getZ() {
        return ParseUtils.parseFloat(getProperty("pos_z"), 0f);
    }

    public long findByName(String name) {
        if (name == null) return 0L;
        for (SceneSnapshot.NodeSnapshot n : ClientSceneBus.copyNodes()) {
            if (n != null && name.equals(n.name())) return n.nodeId();
        }
        return 0L;
    }

    public void writeProperty(String key, String value) {
        ClientPropertyOverrides.put(nodeId, key, value);
    }

    public void writeNumber(String key, double value) {
        ClientPropertyOverrides.put(nodeId, key, Double.toString(value));
    }

    public void writePropertyOf(long targetId, String key, String value) {
        ClientPropertyOverrides.put(targetId, key, value);
    }

    public void writeNumberOf(long targetId, String key, double value) {
        ClientPropertyOverrides.put(targetId, key, Double.toString(value));
    }

    public void clearOverride(String key) {
        ClientPropertyOverrides.put(nodeId, key, null);
    }

    public void clearOverridesOn(long targetId) {
        ClientPropertyOverrides.clearNode(targetId);
    }

    public long spawnLocal(String type, String name, long parentId) {
        return ClientLocalNodes.create(type, name, parentId);
    }

    public void setLocalProp(long id, String key, String value) {
        ClientLocalNodes.setProperty(id, key, value);
    }

    public void setLocalNumber(long id, String key, double value) {
        ClientLocalNodes.setProperty(id, key, Double.toString(value));
    }

    public boolean freeLocal(long id) {
        ClientLocalNodes.free(id);
        ClientPropertyOverrides.clearNode(id);
        return true;
    }

    public void clearLocalNodes() {
        ClientLocalNodes.clearAll();
    }

    public float getWorldX(long id) { return worldPos(id).x; }

    public float getWorldY(long id) { return worldPos(id).y; }

    public float getWorldZ(long id) { return worldPos(id).z; }

    private final Vector3f scratchPos = new Vector3f();

    private Vector3f worldPos(long id) {
        if (id == nodeId) {
            PlayerEntity local = MinecraftClient.getInstance().player;
            if (local != null) {
                scratchPos.set((float) local.getX(), (float) local.getY(), (float) local.getZ());
                return scratchPos;
            }
        }
        SceneNodeTransforms.tryWorldPosition(id, scratchPos);
        return scratchPos;
    }

    private SceneSnapshot.NodeSnapshot findSelf() {
        for (SceneSnapshot.NodeSnapshot n : ClientSceneBus.copyNodes()) {
            if (n != null && n.nodeId() == nodeId) return n;
        }
        return null;
    }

}
