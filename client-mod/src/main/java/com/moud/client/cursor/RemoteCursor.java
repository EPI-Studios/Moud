package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class RemoteCursor {

    private final UUID playerId;
    private boolean visible = false;

    private Identifier texture = Identifier.of("moud", "textures/gui/cursor_default.png");
    private Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
    private float scale = 0.5f;
    private String renderMode = "TEXTURE";

    private Vector3 currentPosition;
    private Vector3 targetPosition;
    private Vector3 currentNormal;
    private Vector3 targetNormal;

    private static final float INTERPOLATION_SPEED = 0.15f;

    public RemoteCursor(UUID playerId) {
        this.playerId = playerId;
        this.currentPosition = Vector3.zero();
        this.targetPosition = Vector3.zero();
        this.currentNormal = new Vector3(0, 1, 0);
        this.targetNormal = new Vector3(0, 1, 0);
    }

    public void update(float tickDelta) {
        float factor = 1.0f - (float)Math.pow(1.0f - INTERPOLATION_SPEED, tickDelta * 60.0f);

        currentPosition = Vector3.lerp(currentPosition, targetPosition, factor);
        currentNormal = Vector3.lerp(currentNormal, targetNormal, factor).normalize();
    }

    public void setTargetPosition(Vector3 pos, Vector3 normal) {
        this.targetPosition = pos;
        this.targetNormal = normal;

        this.currentPosition = pos;
        this.currentNormal = normal;
    }

    public void setAppearance(String texture, Vector3 color, float scale, String renderMode) {
        this.texture = Identifier.tryParse(texture);
        this.color = color;
        this.scale = scale;
        this.renderMode = renderMode;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Identifier getTexture() {
        return texture;
    }

    public Vector3 getColor() {
        return color;
    }

    public float getScale() {
        return scale;
    }

    public Vector3 getCurrentPosition() {
        return currentPosition;
    }

    public Vector3 getCurrentNormal() {
        return currentNormal;
    }
}