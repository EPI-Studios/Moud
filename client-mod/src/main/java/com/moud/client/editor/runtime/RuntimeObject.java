package com.moud.client.editor.runtime;

import com.moud.api.collision.OBB;
import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.display.DisplaySurface;
import com.moud.client.model.RenderableModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
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
    private boolean isPlayerModel;
    private java.util.Map<String, Capsule> limbCapsules;
    private java.util.Map<String, Capsule> baseLimbCapsules;

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

    public boolean isPlayerModel() {
        return isPlayerModel;
    }

    public void updateFromMap(java.util.Map<String, Object> props) {
        if (props == null) return;

        this.label = (String) props.getOrDefault("label", objectId);

        // position
        Object posObj = props.get("position");
        if (posObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> posMap = (java.util.Map<String, Object>) posObj;
            double x = ((Number) posMap.getOrDefault("x", 0.0)).doubleValue();
            double y = ((Number) posMap.getOrDefault("y", 0.0)).doubleValue();
            double z = ((Number) posMap.getOrDefault("z", 0.0)).doubleValue();
            this.position = new Vec3d(x, y, z);
        }

        // rotation
        Object rotObj = props.get("rotation");
        if (rotObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> rotMap = (java.util.Map<String, Object>) rotObj;
            double pitch = ((Number) rotMap.getOrDefault("pitch", 0.0)).doubleValue();
            double yaw = ((Number) rotMap.getOrDefault("yaw", 0.0)).doubleValue();
            double roll = ((Number) rotMap.getOrDefault("roll", 0.0)).doubleValue();
            this.rotation = new Vec3d(pitch, yaw, roll);
        }

        // scale
        Object scaleObj = props.get("scale");
        if (scaleObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> scaleMap = (java.util.Map<String, Object>) scaleObj;
            double x = ((Number) scaleMap.getOrDefault("x", 1.0)).doubleValue();
            double y = ((Number) scaleMap.getOrDefault("y", 1.0)).doubleValue();
            double z = ((Number) scaleMap.getOrDefault("z", 1.0)).doubleValue();
            this.scale = new Vec3d(x, y, z);
        }

        this.bounds = new Box(position.subtract(0.25, 0.25, 0.25), position.add(0.25, 0.25, 0.25));
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

    public void updateBoundsFromCollision(List<OBB> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (OBB obb : boxes) {
            if (obb == null) continue;
            Vec3d c = new Vec3d(obb.center.x, obb.center.y, obb.center.z);
            Vec3d e = new Vec3d(obb.halfExtents.x, obb.halfExtents.y, obb.halfExtents.z);
            minX = Math.min(minX, c.x - e.x);
            minY = Math.min(minY, c.y - e.y);
            minZ = Math.min(minZ, c.z - e.z);
            maxX = Math.max(maxX, c.x + e.x);
            maxY = Math.max(maxY, c.y + e.y);
            maxZ = Math.max(maxZ, c.z + e.z);
        }
        if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
            this.bounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public void updateFromPlayerModel(Vec3d pos, Vec3d rotDeg) {
        this.label = "Fake Player";
        this.isPlayerModel = true;
        this.position = pos;
        this.scale = new Vec3d(1, 1, 1);
        this.rotation = rotDeg != null ? rotDeg : Vec3d.ZERO;
        double halfWidth = 0.3;
        double height = 1.8;
        this.bounds = new Box(
                pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + height, pos.z + halfWidth
        );
        // TODO: NEED MASSIVE REWORK LMAO
        java.util.Map<String, Capsule> caps = new java.util.HashMap<>();
        Vec3d headCenter = new Vec3d(0, height * 0.85, 0);
        caps.put("fakeplayer:head", Capsule.sphere(headCenter, 0.12));

        Vec3d shoulder = new Vec3d(0, height * 0.65, 0);
        Vec3d hip = new Vec3d(0, height * 0.35, 0);
        caps.put("fakeplayer:torso", Capsule.line(shoulder, hip, 0.16));

        Vec3d leftArmTop = shoulder.add(-0.22, 0.05, 0);
        Vec3d leftArmBot = leftArmTop.add(0, -0.35, 0);
        caps.put("fakeplayer:left_arm", Capsule.line(leftArmTop, leftArmBot, 0.1));

        Vec3d rightArmTop = shoulder.add(0.22, 0.05, 0);
        Vec3d rightArmBot = rightArmTop.add(0, -0.35, 0);
        caps.put("fakeplayer:right_arm", Capsule.line(rightArmTop, rightArmBot, 0.1));

        Vec3d leftLegTop = hip.add(-0.1, -0.05, 0);
        Vec3d leftLegBot = leftLegTop.add(0, -0.8, 0);
        caps.put("fakeplayer:left_leg", Capsule.line(leftLegTop, leftLegBot, 0.1));

        Vec3d rightLegTop = hip.add(0.1, -0.05, 0);
        Vec3d rightLegBot = rightLegTop.add(0, -0.8, 0);
        caps.put("fakeplayer:right_leg", Capsule.line(rightLegTop, rightLegBot, 0.1));

        this.baseLimbCapsules = caps;
        rebuildLimbCapsules();
    }

    public java.util.Map<String, Capsule> getLimbCapsules() {
        return limbCapsules;
    }

    public void setPosition(Vec3d newPos) {
        Vec3d delta = newPos.subtract(this.position);
        this.position = newPos;
        if (this.bounds != null) {
            this.bounds = this.bounds.offset(delta.x, delta.y, delta.z);
        }
        rebuildLimbCapsules();
    }

    public void setRotation(Vec3d newRot) {
        this.rotation = newRot;
        rebuildLimbCapsules();
    }

    public void setScale(Vec3d newScale) {
        this.scale = newScale;
        if (this.isPlayerModel) {
            double halfWidth = 0.3 * newScale.x;
            double height = 1.8 * newScale.y;
            this.bounds = new Box(
                    position.x - halfWidth, position.y, position.z - halfWidth,
                    position.x + halfWidth, position.y + height, position.z + halfWidth
            );
        }
        rebuildLimbCapsules();
    }

    private void rebuildLimbCapsules() {
        if (baseLimbCapsules == null || baseLimbCapsules.isEmpty()) {
            return;
        }
        Quaternion q = quaternionFromEuler(rotation);
        double sx = Math.max(1e-4, scale.x);
        double sy = Math.max(1e-4, scale.y);
        double sz = Math.max(1e-4, scale.z);
        java.util.Map<String, Capsule> world = new java.util.HashMap<>();
        for (var entry : baseLimbCapsules.entrySet()) {
            Capsule c = entry.getValue();
            Vec3d la = new Vec3d(c.a().x * sx, c.a().y * sy, c.a().z * sz);
            Vec3d lb = new Vec3d(c.b().x * sx, c.b().y * sy, c.b().z * sz);
            Vec3d wa = position.add(rotateVec(la, q));
            Vec3d wb = position.add(rotateVec(lb, q));
            double radius = c.radius() * Math.max(sx, Math.max(sy, sz));
            world.put(entry.getKey(), Capsule.line(wa, wb, radius));
        }
        this.limbCapsules = world;
    }

    private Vec3d rotateVec(Vec3d v, Quaternion q) {
        double x = v.x, y = v.y, z = v.z;
        double qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        double ix =  qw * x + qy * z - qz * y;
        double iy =  qw * y + qz * x - qx * z;
        double iz =  qw * z + qx * y - qy * x;
        double iw = -qx * x - qy * y - qz * z;

        double rx = ix * qw + iw * -qx + iy * -qz - iz * -qy;
        double ry = iy * qw + iw * -qy + iz * -qx - ix * -qz;
        double rz = iz * qw + iw * -qz + ix * -qy - iy * -qx;
        return new Vec3d(rx, ry, rz);
    }

    private Quaternion quaternionFromEuler(Vec3d eulerDeg) {
        double pitch = Math.toRadians(eulerDeg.x);
        double yaw = Math.toRadians(eulerDeg.y);
        double roll = Math.toRadians(eulerDeg.z);
        double cy = Math.cos(yaw * 0.5);
        double sy = Math.sin(yaw * 0.5);
        double cp = Math.cos(pitch * 0.5);
        double sp = Math.sin(pitch * 0.5);
        double cr = Math.cos(roll * 0.5);
        double sr = Math.sin(roll * 0.5);

        double w = cr * cp * cy + sr * sp * sy;
        double x = sr * cp * cy - cr * sp * sy;
        double y = cr * sp * cy + sr * cp * sy;
        double z = cr * cp * sy - sr * sp * cy;
        return new Quaternion((float) x, (float) y, (float) z, (float) w);
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
