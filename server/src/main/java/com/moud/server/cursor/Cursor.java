package com.moud.server.cursor;

import com.moud.api.math.Vector3;
import net.minestom.server.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Cursor {
    private final Player owner;
    private CursorMode mode = CursorMode.THREE_DIMENSIONAL;
    private boolean isGloballyVisible = true;
    private Set<UUID> visibleTo = new HashSet<>();
    private boolean isWhitelist = false;

    private String texture = "minecraft:textures/block/white_concrete.png";
    private Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
    private float scale = 1.0f;
    private RenderMode renderMode = RenderMode.TEXTURE;
    private boolean projectOntoBlock = false;

    private Vector3 worldPosition = Vector3.zero();
    private Vector3 targetPosition = Vector3.zero();
    private Vector3 worldNormal = new Vector3(0, 1, 0);
    private Vector3 targetNormal = new Vector3(0, 1, 0);
    private boolean hittingBlock = false;

    private static final float INTERPOLATION_SPEED = 0.2f;

    public Cursor(Player owner) {
        this.owner = owner;
        this.targetPosition = worldPosition;
        this.targetNormal = worldNormal;
    }

    public void updateInterpolation(float deltaTime) {
        float factor = 1.0f - (float)Math.pow(1.0f - INTERPOLATION_SPEED, deltaTime * 60.0f);

        worldPosition = Vector3.lerp(worldPosition, targetPosition, factor);
        worldNormal = Vector3.lerp(worldNormal, targetNormal, factor).normalize();
    }

    public void setTargetPosition(Vector3 position, Vector3 normal) {
        this.targetPosition = position;
        this.targetNormal = normal;
    }

    public void snapToTarget() {
        this.worldPosition = this.targetPosition;
        this.worldNormal = this.targetNormal;
    }

    public Player getOwner() {
        return owner;
    }

    public CursorMode getMode() {
        return mode;
    }

    public void setMode(CursorMode mode) {
        this.mode = mode;
    }

    public boolean isGloballyVisible() {
        return isGloballyVisible;
    }

    public void setGloballyVisible(boolean globallyVisible) {
        isGloballyVisible = globallyVisible;
    }

    public Set<UUID> getVisibleTo() {
        return visibleTo;
    }

    public boolean isWhitelist() {
        return isWhitelist;
    }

    public void setVisibilityList(Set<UUID> players, boolean isWhitelist) {
        this.visibleTo = players;
        this.isWhitelist = isWhitelist;
    }

    public String getTexture() {
        return texture;
    }

    public void setTexture(String texture) {
        this.texture = texture;
    }

    public Vector3 getColor() {
        return color;
    }

    public void setColor(Vector3 color) {
        this.color = color;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public boolean isProjectOntoBlock() {
        return projectOntoBlock;
    }

    public void setProjectOntoBlock(boolean projectOntoBlock) {
        this.projectOntoBlock = projectOntoBlock;
    }

    public Vector3 getWorldPosition() {
        return worldPosition;
    }

    public Vector3 getTargetPosition() {
        return targetPosition;
    }

    public void setWorldPosition(Vector3 worldPosition) {
        this.worldPosition = worldPosition;
        this.targetPosition = worldPosition;
    }

    public Vector3 getWorldNormal() {
        return worldNormal;
    }

    public Vector3 getTargetNormal() {
        return targetNormal;
    }

    public void setWorldNormal(Vector3 worldNormal) {
        this.worldNormal = worldNormal;
        this.targetNormal = worldNormal;
    }

    public boolean isHittingBlock() {
        return hittingBlock;
    }

    public void setHittingBlock(boolean hittingBlock) {
        this.hittingBlock = hittingBlock;
    }

    public boolean isVisibleTo(Player viewer) {
        if (!isGloballyVisible) return false;
        if (visibleTo.isEmpty()) return true;
        boolean contains = visibleTo.contains(viewer.getUuid());
        return isWhitelist ? contains : !contains;
    }

    public enum CursorMode {
        THREE_DIMENSIONAL,
        TWO_DIMENSIONAL
    }

    public enum RenderMode {
        TEXTURE,
        // TODO : IMPLEMENT THE REST
        MESH,
        ITEM
    }
}