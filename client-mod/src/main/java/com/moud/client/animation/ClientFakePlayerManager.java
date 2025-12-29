package com.moud.client.animation;

import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class ClientFakePlayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientFakePlayerManager.class);
    private static final ClientFakePlayerManager INSTANCE = new ClientFakePlayerManager();
    private static final String FAKE_PLAYER_PREFIX = "MoudFake_";

    private final ConcurrentHashMap<Long, OtherClientPlayerEntity> fakePlayersByModelId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> modelIdByPlayerName = new ConcurrentHashMap<>();

    private ClientFakePlayerManager() {
    }

    public static ClientFakePlayerManager getInstance() {
        return INSTANCE;
    }


    public static boolean isFakePlayer(OtherClientPlayerEntity player) {
        return player.getName().getString().startsWith(FAKE_PLAYER_PREFIX);
    }


    public static long extractModelId(String playerName) {
        if (!playerName.startsWith(FAKE_PLAYER_PREFIX)) {
            return -1;
        }

        try {
            String afterPrefix = playerName.substring(FAKE_PLAYER_PREFIX.length());
            if (afterPrefix.startsWith("Model")) {
                return Long.parseLong(afterPrefix.substring(5));
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to extract model ID from fake player name: {}", playerName);
        }

        return -1;
    }

    public void registerFakePlayer(OtherClientPlayerEntity player, long modelId) {
        fakePlayersByModelId.put(modelId, player);
        modelIdByPlayerName.put(player.getName().getString(), modelId);

        syncFakePlayer(modelId, player);

        LOGGER.debug("Registered fake player {} with model ID {} in editor", player.getName().getString(), modelId);
    }

    public void autoRegisterFakePlayer(OtherClientPlayerEntity player) {
        if (isFakePlayer(player)) {
            long modelId = extractModelId(player.getName().getString());
            if (modelId != -1) {
                registerFakePlayer(player, modelId);
            }
        }
    }


    public void unregisterFakePlayer(long modelId) {
        OtherClientPlayerEntity player = fakePlayersByModelId.remove(modelId);
        if (player != null) {
            modelIdByPlayerName.remove(player.getName().getString());

            RuntimeObjectRegistry.getInstance().removePlayerModel(modelId);

            LOGGER.debug("Unregistered fake player with model ID {} from editor", modelId);
        }
    }

    public OtherClientPlayerEntity getFakePlayer(long modelId) {
        return fakePlayersByModelId.get(modelId);
    }

    public Long getModelIdByName(String playerName) {
        return modelIdByPlayerName.get(playerName);
    }

    public void scanAndRegisterFakePlayers() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;

        if (world == null) {
            return;
        }

        world.getPlayers().forEach(player -> {
            if (player instanceof OtherClientPlayerEntity otherPlayer) {
                autoRegisterFakePlayer(otherPlayer);
            }
        });

        LOGGER.info("Scanned world and registered {} fake players", fakePlayersByModelId.size());
    }

    public void clear() {
        int count = fakePlayersByModelId.size();
        fakePlayersByModelId.clear();
        modelIdByPlayerName.clear();
        LOGGER.info("Cleared {} registered fake players", count);
    }

    public int getFakePlayerCount() {
        return fakePlayersByModelId.size();
    }

    public void updatePositions() {
        fakePlayersByModelId.forEach(this::syncFakePlayer);
    }

    private void syncFakePlayer(long modelId, OtherClientPlayerEntity player) {
        RuntimeObjectRegistry.getInstance().syncFakePlayer(modelId, player);
    }
}
