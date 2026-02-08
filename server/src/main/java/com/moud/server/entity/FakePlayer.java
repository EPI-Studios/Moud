package com.moud.server.entity;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;


public class FakePlayer extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger(FakePlayer.class);
    private static final String FAKE_PLAYER_PREFIX = "MoudFake_";

    private final String username;
    private final long modelId;
    private String skinTexture;
    private String skinSignature;


    public FakePlayer(long modelId, @NotNull String username) {
        this(modelId, UUID.randomUUID(), username);
    }

    public FakePlayer(long modelId, @NotNull UUID uuid, @NotNull String username) {
        super(EntityType.PLAYER, uuid);
        this.modelId = modelId;
        this.username = FAKE_PLAYER_PREFIX + username;

        initializeFakePlayer();
    }

    private void initializeFakePlayer() {
        setNoGravity(true);
        setAutoViewable(true);

        LOGGER.debug("Created FakePlayer: {} (modelId: {}, UUID: {})",
                     username, modelId, getUuid());
    }

    public String getUsername() {
        return username;
    }

    public static boolean isFakePlayer(@NotNull Entity entity) {
        if (entity instanceof FakePlayer) {
            return true;
        }
        return false;
    }

    public long getModelId() {
        return modelId;
    }

    public void setSkin(String texture, String signature) {
        this.skinTexture = texture;
        this.skinSignature = signature;

        getViewers().forEach(this::updateSkinForViewer);
    }

    private void updateSkinForViewer(Player viewer) {
        viewer.sendPacket(new PlayerInfoRemovePacket(getUuid()));

        var properties = new ArrayList<PlayerInfoUpdatePacket.Property>();
        if (skinTexture != null && skinSignature != null) {
            properties.add(new PlayerInfoUpdatePacket.Property("textures", skinTexture, skinSignature));
        }
        var entry = new PlayerInfoUpdatePacket.Entry(getUuid(), username, properties, false,
                0, GameMode.SURVIVAL, null, null);
        viewer.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry));
    }

    @Override
    public void updateNewViewer(@NotNull Player player) {
        var properties = new ArrayList<PlayerInfoUpdatePacket.Property>();
        if (skinTexture != null && skinSignature != null) {
            properties.add(new PlayerInfoUpdatePacket.Property("textures", skinTexture, skinSignature));
        }
        var entry = new PlayerInfoUpdatePacket.Entry(getUuid(), username, properties, false,
                0, GameMode.SURVIVAL, null, null);
        player.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry));

        super.updateNewViewer(player);

        player.sendPackets(new EntityMetaDataPacket(getEntityId(), Map.of(17, Metadata.Byte((byte) 127))));
    }

    @Override
    public void updateOldViewer(@NotNull Player player) {
        super.updateOldViewer(player);

        player.sendPacket(new PlayerInfoRemovePacket(getUuid()));
    }

    public void removeFakePlayer() {
        if (getInstance() != null) {
            remove();
        }
        LOGGER.debug("Removed FakePlayer: {} (modelId: {})", username, modelId);
    }

    @Override
    public String toString() {
        return String.format("FakePlayer{modelId=%d, username=%s, uuid=%s, position=%s}",
                           modelId, username, getUuid(), getPosition());
    }
}
