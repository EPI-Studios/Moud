package com.moud.client.fabric.scripting.api;

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

    public NodeApi(long nodeId) {
        this.nodeId = nodeId;
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
