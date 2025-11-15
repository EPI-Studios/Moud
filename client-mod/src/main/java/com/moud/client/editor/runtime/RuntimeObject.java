package com.moud.client.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.display.DisplaySurface;
import com.moud.client.model.RenderableModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class RuntimeObject {
    private final RuntimeObjectType type;
    private final long runtimeId;
    private final String objectId;
    private String label;
    private Vec3d position = Vec3d.ZERO;
    private Vec3d scale = new Vec3d(1, 1, 1);
    private Vec3d rotation = Vec3d.ZERO; // degrees
    private Box bounds = new Box(Vec3d.ZERO, Vec3d.ZERO);
    private String modelPath;
    private String texturePath;

    private DisplayState displayState;
    private UUID playerUuid;

    public RuntimeObject(RuntimeObjectType type, long runtimeId) {
        this(type, runtimeId, type.name().toLowerCase() + ":" + runtimeId);
    }

    public RuntimeObject(RuntimeObjectType type, long runtimeId, String objectId) {
        this.type = type;
        this.runtimeId = runtimeId;
        this.objectId = objectId;
    }

    public RuntimeObjectType getType() {
        return type;
    }

    public long getRuntimeId() {
        return runtimeId;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getLabel() {
        return label != null ? label : objectId;
    }

    public Vec3d getPosition() {
        return position;
    }

    public Vec3d getScale() {
        return scale;
    }

    public Vec3d getRotation() {
        return rotation;
    }

    public Box getBounds() {
        return bounds;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public DisplayState getDisplayState() {
        return displayState;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void updateFromModel(RenderableModel model) {
        this.label = model.getModelPath();
        this.modelPath = model.getModelPath();
        this.texturePath = model.getTexture() != null ? model.getTexture().toString() : "";
        Vector3 pos = model.getPosition();
        if (pos != null) {
            this.position = new Vec3d(pos.x, pos.y, pos.z);
        }
        Vector3 scl = model.getScale();
        if (scl != null) {
            this.scale = new Vec3d(scl.x, scl.y, scl.z);
        }
        Quaternion rot = model.getRotation();
        if (rot != null) {
            this.rotation = quaternionToEuler(rot);
        }
        Vec3d min = Vec3d.ZERO;
        Vec3d max = Vec3d.ZERO;
        if (model.hasMeshBounds()) {
            Vector3 meshMin = model.getMeshMin();
            Vector3 meshMax = model.getMeshMax();
            Vec3d halfScale = new Vec3d(scale.x, scale.y, scale.z);
            min = position.add(meshMin.x * halfScale.x, meshMin.y * halfScale.y, meshMin.z * halfScale.z);
            max = position.add(meshMax.x * halfScale.x, meshMax.y * halfScale.y, meshMax.z * halfScale.z);
        } else {
            min = position.subtract(scale.multiply(0.5));
            max = position.add(scale.multiply(0.5));
        }
        this.bounds = new Box(min, max);
    }

    public void updateFromDisplay(DisplaySurface surface) {
        if (displayState == null) {
            displayState = new DisplayState();
        }
        this.label = surface.getPrimarySource() != null ? surface.getPrimarySource() : ("Display " + runtimeId);
        Vector3 pos = surface.getPosition();
        Vector3 scl = surface.getScale();
        Quaternion rot = surface.getRotation();
        this.position = pos != null ? new Vec3d(pos.x, pos.y, pos.z) : Vec3d.ZERO;
        this.scale = scl != null ? new Vec3d(scl.x, scl.y, scl.z) : new Vec3d(1, 1, 1);
        this.rotation = rot != null ? quaternionToEuler(rot) : Vec3d.ZERO;
        Vec3d half = new Vec3d(Math.max(0.01, scale.x) * 0.5, Math.max(0.01, scale.y) * 0.5, Math.max(0.01, scale.z) * 0.5);
        this.bounds = new Box(position.subtract(half), position.add(half));
        displayState.contentType = surface.getContentType();
        displayState.primarySource = surface.getPrimarySource();
        displayState.loop = surface.isLooping();
        displayState.frameRate = surface.getFrameRate();
    }

    public void updateFromPlayer(AbstractClientPlayerEntity player) {
        this.playerUuid = player.getUuid();
        this.label = player.getName().getString();
        this.position = player.getPos();
        this.scale = new Vec3d(1, 1, 1);
        this.rotation = new Vec3d(player.getPitch(), player.getYaw(), 0);
        Box box = player.getBoundingBox();
        this.bounds = new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private Vec3d quaternionToEuler(Quaternion q) {
        double ysqr = q.y * q.y;

        double t0 = +2.0 * (q.w * q.x + q.y * q.z);
        double t1 = +1.0 - 2.0 * (q.x * q.x + ysqr);
        double roll = Math.toDegrees(Math.atan2(t0, t1));

        double t2 = +2.0 * (q.w * q.y - q.z * q.x);
        t2 = Math.max(-1.0, Math.min(1.0, t2));
        double pitch = Math.toDegrees(Math.asin(t2));

        double t3 = +2.0 * (q.w * q.z + q.x * q.y);
        double t4 = +1.0 - 2.0 * (ysqr + q.z * q.z);
        double yaw = Math.toDegrees(Math.atan2(t3, t4));

        return new Vec3d(pitch, yaw, roll);
    }

    public static final class DisplayState {
        private com.moud.network.MoudPackets.DisplayContentType contentType;
        private String primarySource;
        private boolean loop;
        private float frameRate;

        public com.moud.network.MoudPackets.DisplayContentType getContentType() {
            return contentType;
        }

        public String getPrimarySource() {
            return primarySource;
        }

        public boolean isLoop() {
            return loop;
        }

        public float getFrameRate() {
            return frameRate;
        }
    }
}
