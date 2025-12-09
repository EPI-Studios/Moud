package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@TsExpose
public class PlayerModelProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelProxy.class);
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, PlayerModelProxy> ALL_MODELS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService ANIMATION_SCHEDULER = Executors.newScheduledThreadPool(1);
    private static final int WALK_UPDATE_PERIOD_MS = 16; // ~60hz for smoother waypoint motion

    private final long modelId;
    private Vector3 position;
    private String instanceName;
    private float yaw;
    private float pitch;
    private String skinUrl;
    private String currentAnimation;
    private boolean autoAnimation = true;
    private String manualAnimation;
    private boolean loopAnimation = true;
    private int animationDurationMs = 2000;
    private Value clickCallback;

    private enum MovementState {
        IDLE,
        WALKING
    }
    private MovementState movementState = MovementState.IDLE;
    private Vector3 walkTarget = null;
    private float walkSpeed = 2.0f; // meters per second
    private ScheduledFuture<?> walkTask;

    static {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                Player player = event.getPlayer();
                ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
                if (networkManager == null) return;

                for (PlayerModelProxy model : ALL_MODELS.values()) {
                    networkManager.send(player, new MoudPackets.PlayerModelCreatePacket(model.modelId, model.position, model.skinUrl));
                    networkManager.send(player, new MoudPackets.PlayerModelUpdatePacket(model.modelId, model.position, model.yaw, model.pitch, model.instanceName));

                    if (model.currentAnimation != null && !model.currentAnimation.isEmpty()) {
                        networkManager.send(player, new MoudPackets.S2C_PlayModelAnimationPacket(model.modelId, model.currentAnimation));
                    }
                }
            }
        });
    }

    public PlayerModelProxy(Vector3 position, String skinUrl) {
        this.modelId = ID_COUNTER.getAndIncrement();
        this.position = position != null ? position : Vector3.zero();
        this.skinUrl = skinUrl != null ? skinUrl : "";
        this.yaw = 0.0f;
        this.pitch = 0.0f;

        ALL_MODELS.put(modelId, this);
        broadcastCreate();

        setMovementState(MovementState.IDLE);

    }

    private void setMovementState(MovementState newState) {
        if (this.movementState == newState) return;

        this.movementState = newState;
        LOGGER.debug("Model {} changing state to {}", modelId, newState);

        updateStateAnimation();
    }

    private void updateStateAnimation() {
        if (manualAnimation != null || !autoAnimation) {
            return;
        }
        switch (movementState) {
            case IDLE -> playAnimation("moud:player_animations/idle");
            case WALKING -> playAnimation("moud:player_animations/walk");
        }
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setInstance(String instance) {
        this.instanceName = instance;
        broadcastUpdate();
    }

    @HostAccess.Export
    public String getInstanceName() {
        return instanceName;
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        if (isWalking()) {
            stopWalking();
        }
        this.position = position;
        broadcastUpdate();
    }

    @HostAccess.Export
    public void walkTo(Vector3 target, Value options) {
        if (isWalking()) {
            stopWalking();
        }

        this.walkTarget = target;
        this.walkSpeed = 2.0f;

        if (options != null && options.hasMembers() && options.hasMember("speed")) {
            Value speedValue = options.getMember("speed");
            if (speedValue.isNumber()) {
                this.walkSpeed = speedValue.asFloat();
            }
        }

        setMovementState(MovementState.WALKING);

        walkTask = ANIMATION_SCHEDULER.scheduleAtFixedRate(this::updateWalking, 0, WALK_UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    @HostAccess.Export
    public void stopWalking() {
        if (!isWalking()) return;

        if (walkTask != null) {
            walkTask.cancel(false);
            walkTask = null;
        }
        this.walkTarget = null;

        setMovementState(MovementState.IDLE);
    }

    @HostAccess.Export
    public boolean isWalking() {
        return this.movementState == MovementState.WALKING;
    }

    private void updateWalking() {
        if (!isWalking() || walkTarget == null) {
            stopWalking();
            return;
        }

        float moveDistancePerTick = walkSpeed * (WALK_UPDATE_PERIOD_MS / 1000.0f);
        Vector3 direction = walkTarget.subtract(position);
        float distanceToTarget = direction.length();

        if (distanceToTarget < moveDistancePerTick) {
            this.position = walkTarget;
            broadcastUpdate();
            stopWalking();
            return;
        }

        Vector3 dirNorm = direction.normalize();
        Vector3 velocity = dirNorm.multiply(moveDistancePerTick);
        this.position = this.position.add(velocity);

        if (dirNorm.lengthSquared() > 0.0001f) {
            this.yaw = (float) Math.toDegrees(Math.atan2(-dirNorm.x, dirNorm.z));
        }

        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotation(Value rotationValue) {
        if (rotationValue != null && rotationValue.hasMembers()) {
            if (rotationValue.hasMember("yaw")) {
                Value v = rotationValue.getMember("yaw");
                if (v != null) {
                    this.yaw = v.fitsInFloat() ? v.asFloat() : (float) v.asDouble();
                }
            }
            if (rotationValue.hasMember("pitch")) {
                Value v = rotationValue.getMember("pitch");
                if (v != null) {
                    this.pitch = v.fitsInFloat() ? v.asFloat() : (float) v.asDouble();
                }
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
    public void playAnimationWithFade(String animationName, int durationMs) {
        int durationTicks = durationMs / 50;

        MoudPackets.S2C_PlayModelAnimationWithFadePacket packet = new MoudPackets.S2C_PlayModelAnimationWithFadePacket(
                this.modelId,
                animationName,
                durationTicks
        );
        broadcast(packet);
    }

    @HostAccess.Export
    public void playAnimation(String animationName) {
        this.currentAnimation = animationName;
        broadcastAnimation();
    }

    @HostAccess.Export
    public void setManualAnimation(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            this.manualAnimation = null;
            if (autoAnimation) {
                updateStateAnimation();
            } else {
                stopAnimation();
            }
            return;
        }
        this.manualAnimation = animationName;
        if (loopAnimation) {
            playAnimation(animationName);
        } else {
            playAnimationWithFade(animationName, animationDurationMs);
        }
    }

    @HostAccess.Export
    public void clearManualAnimation() {
        setManualAnimation(null);
    }

    @HostAccess.Export
    public void setAutoAnimation(boolean enabled) {
        this.autoAnimation = enabled;
        if (autoAnimation && manualAnimation == null) {
            updateStateAnimation();
        } else if (!autoAnimation && manualAnimation == null) {
            stopAnimation();
        }
    }

    @HostAccess.Export
    public void setLoopAnimation(boolean loop) {
        this.loopAnimation = loop;
    }

    @HostAccess.Export
    public void setAnimationDuration(int durationMs) {
        if (durationMs > 0) {
            this.animationDurationMs = durationMs;
        }
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
        LOGGER.info("Broadcasting PlayerModelCreatePacket for model {} at position {}", modelId, position);
        broadcast(packet);
    }

    private void broadcastUpdate() {
        MoudPackets.PlayerModelUpdatePacket packet = new MoudPackets.PlayerModelUpdatePacket(modelId, position, yaw, pitch, instanceName);
        broadcast(packet);
    }

    private void broadcastSkin() {
        MoudPackets.PlayerModelSkinPacket packet = new MoudPackets.PlayerModelSkinPacket(modelId, skinUrl);
        broadcast(packet);
    }

    private void broadcastAnimation() {
        MoudPackets.S2C_PlayModelAnimationPacket packet = new MoudPackets.S2C_PlayModelAnimationPacket(modelId, currentAnimation);
        broadcast(packet);
    }

    private void stopAnimation() {
        MoudPackets.S2C_PlayModelAnimationPacket packet = new MoudPackets.S2C_PlayModelAnimationPacket(modelId, "");
        broadcast(packet);
    }

    private void broadcastRemove() {
        MoudPackets.PlayerModelRemovePacket packet = new MoudPackets.PlayerModelRemovePacket(modelId);
        broadcast(packet);
    }

    private void broadcast(Object packet) {
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager != null) {
            LOGGER.debug("Broadcasting packet {} via ServerNetworkManager", packet.getClass().getSimpleName());
            networkManager.broadcast(packet);
        } else {
            LOGGER.error("ServerNetworkManager is null! Cannot broadcast packet {}", packet.getClass().getSimpleName());
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
                clickCallback.execute(new PlayerProxy(player), ProxyObject.fromMap(clickData));
            } catch (Exception e) {
                LOGGER.error("Error executing PlayerModel click callback", e);
            }
        }
    }

    public long getModelId() {
        return modelId;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public String getCurrentAnimation() {
        return currentAnimation;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

}
