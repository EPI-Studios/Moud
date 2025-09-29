package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerPacketWrapper;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerModelProxy {
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, PlayerModelProxy> ALL_MODELS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService ANIMATION_SCHEDULER = Executors.newScheduledThreadPool(1);

    private final long modelId;
    private Vector3 position;
    private float yaw;
    private float pitch;
    private String skinUrl;
    private String currentAnimation;
    private Value clickCallback;

    private boolean isWalking = false;
    private Vector3 walkTarget = null;
    private float walkSpeed = 2.0f;
    private ScheduledFuture<?> walkTask;

    static {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                Player player = event.getPlayer();
                for (PlayerModelProxy model : ALL_MODELS.values()) {
                    player.sendPacket(ServerPacketWrapper.createPacket(
                            new MoudPackets.PlayerModelCreatePacket(model.modelId, model.position, model.skinUrl)
                    ));
                    player.sendPacket(ServerPacketWrapper.createPacket(
                            new MoudPackets.PlayerModelUpdatePacket(model.modelId, model.position, model.yaw, model.pitch)
                    ));
                }
            }
        });
    }

    public PlayerModelProxy(Vector3 position, String skinUrl) {
        this.modelId = ID_COUNTER.getAndIncrement();
        this.position = position;
        this.skinUrl = skinUrl != null ? skinUrl : "";
        this.yaw = 0.0f;
        this.pitch = 0.0f;

        ALL_MODELS.put(modelId, this);
        broadcastCreate();


        playAnimation("moud:idle");
    }



    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        this.position = position;
        broadcastUpdate();
    }

    @HostAccess.Export
    public void walkTo(Vector3 target, Value options) {
        if (isWalking) {
            stopWalking();
        }

        this.walkTarget = target;
        this.isWalking = true;
        this.walkSpeed = 2.0f;

        if (options != null && options.hasMembers() && options.hasMember("speed")) {
            this.walkSpeed = options.getMember("speed").asFloat();
        }

        float deltaX = target.x - position.x;
        float deltaZ = target.z - position.z;
        this.yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        playAnimation("moud:walk");

        walkTask = ANIMATION_SCHEDULER.scheduleAtFixedRate(this::updateWalking, 0, 50, TimeUnit.MILLISECONDS);
    }

    @HostAccess.Export
    public void stopWalking() {
        if (!isWalking) return;

        this.isWalking = false;
        this.walkTarget = null;
        if (walkTask != null) {
            walkTask.cancel(false);
            walkTask = null;
        }
        playAnimation("moud:idle");
    }

    @HostAccess.Export
    public boolean isWalking() {
        return this.isWalking;
    }

    private void updateWalking() {
        if (!isWalking || walkTarget == null) {
            if (walkTask != null) {
                walkTask.cancel(false);
            }
            return;
        }

        Vector3 direction = walkTarget.subtract(position);
        float distance = direction.length();
        float moveThreshold = walkSpeed / 20.0f;

        if (distance < moveThreshold) {
            this.position = walkTarget;
            stopWalking();
            broadcastUpdate();
            return;
        }

        Vector3 velocity = direction.normalize().multiply(moveThreshold);
        this.position = this.position.add(velocity);

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
        this.skinUrl = skinUrl != null ? skinUrl : "";
        broadcastSkin();
    }

    @HostAccess.Export
    public void playAnimation(String animationName) {
        this.currentAnimation = animationName;
        broadcastAnimation();
    }

    @HostAccess.Export
    public void remove() {
        stopWalking();
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
        MoudPackets.PlayerModelCreatePacket packet = new MoudPackets.PlayerModelCreatePacket(modelId, position, skinUrl);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }

    private void broadcastUpdate() {
        MoudPackets.PlayerModelUpdatePacket packet = new MoudPackets.PlayerModelUpdatePacket(modelId, position, yaw, pitch);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }

    private void broadcastSkin() {
        MoudPackets.PlayerModelSkinPacket packet = new MoudPackets.PlayerModelSkinPacket(modelId, skinUrl);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }

    private void broadcastAnimation() {
        MoudPackets.S2C_PlayModelAnimationPacket packet = new MoudPackets.S2C_PlayModelAnimationPacket(modelId, currentAnimation);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }

    private void broadcastRemove() {
        MoudPackets.PlayerModelRemovePacket packet = new MoudPackets.PlayerModelRemovePacket(modelId);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
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
                Map<String, Object> clickData = Map.of(
                        "button", button,
                        "mouseX", mouseX,
                        "mouseY", mouseY
                );
                ProxyObject data = ProxyObject.fromMap(clickData);
                clickCallback.execute(new PlayerProxy(player), data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public long getModelId() {
        return modelId;
    }

    public String getSkinUrl() {
        return skinUrl;
    }
}