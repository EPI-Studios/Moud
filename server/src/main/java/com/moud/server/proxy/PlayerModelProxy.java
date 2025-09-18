package com.moud.server.proxy;

import com.moud.server.MoudEngine;
import com.moud.api.math.Vector3;
import com.moud.server.events.EventDispatcher;
import com.moud.server.network.PlayerModelPackets;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerModelProxy {
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, PlayerModelProxy> ALL_MODELS = new ConcurrentHashMap<>();

    private final long modelId;
    private Vector3 position;
    private float yaw;
    private float pitch;
    private String skinUrl;
    private String currentAnimation;
    private Value clickCallback;

    static {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                Player player = event.getPlayer();
                for (PlayerModelProxy model : ALL_MODELS.values()) {
                    player.sendPacket(PlayerModelPackets.createPlayerModelCreatePacket(model.modelId, model.position, model.skinUrl));
                    player.sendPacket(PlayerModelPackets.createPlayerModelUpdatePacket(model.modelId, model.position, model.yaw, model.pitch));
                }
            }
        });
    }


    public PlayerModelProxy(Vector3 position, String skinUrl) {
        this.modelId = ID_COUNTER.getAndIncrement();
        this.position = position;
        this.skinUrl = skinUrl;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
        ALL_MODELS.put(modelId, this);
        broadcastCreate();
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        this.position = position;
        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotation(Value rotationValue) {
        if (rotationValue != null && rotationValue.hasMembers()) {
            if (rotationValue.hasMember("yaw")) {
                this.yaw = rotationValue.getMember("yaw").asFloat();
            }
            if (rotationValue.hasMember("pitch")) {
                this.pitch = rotationValue.getMember("pitch").asFloat();
            }
            broadcastUpdate();
        }
    }

    @HostAccess.Export
    public void setSkin(String skinUrl) {
        this.skinUrl = skinUrl;
        broadcastSkin();
    }

    @HostAccess.Export
    public void playAnimation(String animationName) {
        this.currentAnimation = animationName;
        broadcastAnimation();
    }

    @HostAccess.Export
    public void remove() {
        ALL_MODELS.remove(modelId);
        broadcastRemove();
    }

    @HostAccess.Export
    public void onClick(Value callback) {
        if (callback != null && callback.canExecute()) {
            this.clickCallback = callback;
        }
    }

    private void broadcastCreate() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(PlayerModelPackets.createPlayerModelCreatePacket(modelId, position, skinUrl));
        }
    }

    private void broadcastUpdate() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(PlayerModelPackets.createPlayerModelUpdatePacket(modelId, position, yaw, pitch));
        }
    }

    private void broadcastSkin() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(PlayerModelPackets.createPlayerModelSkinPacket(modelId, skinUrl));
        }
    }

    private void broadcastAnimation() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(PlayerModelPackets.createPlayerModelAnimationPacket(modelId, currentAnimation));
        }
    }

    private void broadcastRemove() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(PlayerModelPackets.createPlayerModelRemovePacket(modelId));
        }
    }

    public static PlayerModelProxy getById(long modelId) {
        return ALL_MODELS.get(modelId);
    }

    public static Collection<PlayerModelProxy> getAllModels() {
        return ALL_MODELS.values();
    }

    public void triggerClick(Player player, double mouseX, double mouseY, int button) {
        if (clickCallback != null) {
            try {
                Value clickData = Value.asValue(new ConcurrentHashMap<String, Object>());
                clickData.putMember("button", button);
                clickData.putMember("mouseX", mouseX);
                clickData.putMember("mouseY", mouseY);

                clickCallback.execute(new PlayerProxy(player), clickData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public long getModelId() {
        return modelId;
    }

    public Vector3 getPosition() {
        return position;
    }

    public String getSkinUrl() {
        return skinUrl;
    }
}