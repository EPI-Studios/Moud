package com.moud.client.camera;

import com.moud.api.math.Vector3;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;

public abstract class CameraAPI {

    protected boolean enabled = false;
    protected Vector3 position = Vector3.zero();
    public Vector3 prevPosition = Vector3.zero();
    public Vector3 targetPosition = Vector3.zero();
    protected float yaw = 0.0f;
    protected float pitch = 0.0f;
    protected float roll = 0.0f;
    protected float fov = 70.0f;
    protected Entity trackedEntity = null;
    protected boolean globalMode = false;
    protected boolean chunkLoadingEnabled = false;
    protected boolean collisionEnabled = false;

    public abstract void initialize();

    public abstract void tick(float partialTicks);

    public abstract void applyTransformation(Camera camera);

    public abstract void handleInput(double deltaX, double deltaY, double deltaZ);

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    protected void onEnable() {
        prevPosition = position;
        targetPosition = position;
    }

    protected void onDisable() {
    }

    public void setPosition(double x, double y, double z) {
        this.position = new Vector3((float)x, (float)y, (float)z);
    }

    public void setPosition(Vector3 pos) {
        this.position = pos;
    }

    public void addPosition(double x, double y, double z) {
        this.position = this.position.add(new Vector3((float)x, (float)y, (float)z));
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getPrevPosition() {
        return prevPosition;
    }

    public Vector3 getTargetPosition() {
        return targetPosition;
    }

    public void setRotation(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getRoll() {
        return roll;
    }

    public void setFOV(float fov) {
        this.fov = Math.max(1.0f, Math.min(170.0f, fov));
    }

    public float getFOV() {
        return fov;
    }

    public void moveForward(double distance) {
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        float x = (float)(-Math.sin(radYaw) * Math.cos(radPitch) * distance);
        float y = (float)(-Math.sin(radPitch) * distance);
        float z = (float)(Math.cos(radYaw) * Math.cos(radPitch) * distance);

        addPosition(x, y, z);
    }

    public void moveRight(double distance) {
        double radYaw = Math.toRadians(yaw + 90);
        float x = (float)(-Math.sin(radYaw) * distance);
        float z = (float)(Math.cos(radYaw) * distance);

        addPosition(x, 0, z);
    }

    public void moveUp(double distance) {
        addPosition(0, distance, 0);
    }

    public void trackEntity(Entity entity) {
        this.trackedEntity = entity;

    }

    public void stopTracking() {
        this.trackedEntity = null;
    }

    public boolean isTrackingEntity() {
        return trackedEntity != null;
    }

    public void setGlobalMode(boolean global) {
        this.globalMode = global;
    }

    public boolean isGlobalMode() {
        return globalMode;
    }

    public void setChunkLoadingEnabled(boolean enabled) {
        this.chunkLoadingEnabled = enabled;
    }

    public boolean isChunkLoadingEnabled() {
        return chunkLoadingEnabled;
    }

    public void setCollisionEnabled(boolean enabled) {
        this.collisionEnabled = enabled;
    }

    public boolean isCollisionEnabled() {
        return collisionEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        position = Vector3.zero();
        prevPosition = Vector3.zero();
        targetPosition = Vector3.zero();
        yaw = 0.0f;
        pitch = 0.0f;
        roll = 0.0f;
        fov = 70.0f;
        trackedEntity = null;
        globalMode = false;
        chunkLoadingEnabled = false;
        collisionEnabled = false;
    }

    protected Vector3 lerp(Vector3 start, Vector3 end, float factor) {
        return start.lerp(end, factor);
    }

    protected float lerpAngle(float start, float end, float factor) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * factor;
    }
}