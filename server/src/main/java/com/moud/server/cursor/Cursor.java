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

    private String texture = "moud:textures/gui/cursor_default.png";
    private Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
    private float scale = 1.0f;
    private RenderMode renderMode = RenderMode.TEXTURE;

    private Vector3 worldPosition = Vector3.zero();
    private Vector3 worldNormal = new Vector3(0, 1, 0);
    private boolean hittingBlock = false;

    public Cursor(Player owner) {
        this.owner = owner;
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

    public Vector3 getWorldPosition() {
        return worldPosition;
    }

    public void setWorldPosition(Vector3 worldPosition) {
        this.worldPosition = worldPosition;
    }

    public Vector3 getWorldNormal() {
        return worldNormal;
    }

    public void setWorldNormal(Vector3 worldNormal) {
        this.worldNormal = worldNormal;
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
        MESH,
        ITEM
    }
}