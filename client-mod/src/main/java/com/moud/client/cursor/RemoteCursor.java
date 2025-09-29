package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class RemoteCursor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCursor.class);
    private final UUID playerId;
    private boolean visible = false;
    private boolean enabled = true;

    private Identifier texture;
    private Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
    private float scale = 0.8f;
    private String renderMode = "TEXTURE";

    private Vector3 currentPosition;
    private Vector3 previousPosition;
    private Vector3 currentNormal;
    private Vector3 previousNormal;

    public RemoteCursor(UUID playerId) {
        this.playerId = playerId;
        this.currentPosition = Vector3.zero();
        this.previousPosition = Vector3.zero();
        this.currentNormal = new Vector3(0, 1, 0);
        this.previousNormal = new Vector3(0, 1, 0);
        setDefaultTexture();
        LOGGER.info("Created RemoteCursor for player {}", playerId);
    }

    private void setDefaultTexture() {
        this.texture = Identifier.of("minecraft", "textures/block/white_concrete.png");
        LOGGER.debug("Set default texture for cursor {}: {}", playerId, texture);
    }

    public void update(float tickDelta) {
    }

    public void setTargetPosition(Vector3 pos, Vector3 normal) {
        this.previousPosition = this.currentPosition;
        this.previousNormal = this.currentNormal;
        this.currentPosition = pos;
        this.currentNormal = normal;
        LOGGER.debug("Updated cursor {} position: prev={}, current={}", playerId, previousPosition, currentPosition);
    }

    public Vector3 getInterpolatedPosition(float tickProgress) {
        return Vector3.lerp(previousPosition, currentPosition, tickProgress);
    }

    public Vector3 getInterpolatedNormal(float tickProgress) {
        return Vector3.lerp(previousNormal, currentNormal, tickProgress).normalize();
    }

    public void setAppearance(String textureString, Vector3 color, float scale, String renderMode) {
        LOGGER.info("Setting appearance for cursor {}: texture={}, color={}, scale={}", playerId, textureString, color, scale);

        if (textureString != null && !textureString.isEmpty()) {
            Identifier parsedTexture = Identifier.tryParse(textureString);
            if (parsedTexture != null) {
                this.texture = parsedTexture;
                LOGGER.debug("Successfully set texture to {}", parsedTexture);
            } else {
                LOGGER.warn("Failed to parse texture {}, using default", textureString);
                setDefaultTexture();
            }
        } else {
            LOGGER.warn("Empty texture string, using default");
            setDefaultTexture();
        }

        this.color = color != null ? color : new Vector3(1.0f, 1.0f, 1.0f);
        this.scale = scale;
        this.renderMode = renderMode != null ? renderMode : "TEXTURE";
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isVisible() {
        return visible && enabled;
    }

    public void setVisible(boolean visible) {
        LOGGER.info("Setting cursor {} visibility to {}", playerId, visible);
        this.visible = visible;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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