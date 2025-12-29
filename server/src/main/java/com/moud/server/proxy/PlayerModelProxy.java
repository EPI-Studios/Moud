package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.entity.FakePlayer;
import com.moud.server.instance.InstanceManager;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.scripting.PolyglotValueUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
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
    private static final int WALK_UPDATE_PERIOD_MS = 50; // 20 TPS - match Minecraft's tick rate

    private final long modelId;
    private final FakePlayer fakePlayer;
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

    public PlayerModelProxy(Vector3 position, String skinUrl) {
        this.modelId = ID_COUNTER.getAndIncrement();
        this.position = position != null ? position : Vector3.zero();
        this.skinUrl = skinUrl != null ? skinUrl : "";
        this.yaw = 0.0f;
        this.pitch = 0.0f;

        this.fakePlayer = new FakePlayer(modelId, "Model" + modelId);

        Instance defaultInstance = MinecraftServer.getInstanceManager().getInstances().stream()
                .findFirst()
                .orElse(null);

        if (defaultInstance == null) {
            LOGGER.error("No instance available to spawn FakePlayer {}", modelId);
            throw new RuntimeException("Cannot spawn FakePlayer: no instance available");
        }

        Pos spawnPos = new Pos(position.x, position.y, position.z, yaw, pitch);
        fakePlayer.setInstance(defaultInstance, spawnPos).thenRun(() -> {
            LOGGER.info("FakePlayer {} spawned at position {} in instance {}",
                       modelId, spawnPos, defaultInstance.getUniqueId());

            if (skinUrl != null && !skinUrl.isEmpty()) {
                applySkinToFakePlayer(skinUrl);
            }
        });

        ALL_MODELS.put(modelId, this);
        setMovementState(MovementState.IDLE);
    }

    private void setMovementState(MovementState newState) {
        if (this.movementState == newState) return;

        this.movementState = newState;
        LOGGER.debug("Model {} changing state to {}", modelId, newState);

    }

    private void updateStateAnimation() {
        if (manualAnimation != null || !autoAnimation) {
            return;
        }
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setInstance(String instanceName) {
        this.instanceName = instanceName;

        Instance targetInstance = MinecraftServer.getInstanceManager().getInstances().stream()
                .filter(inst -> inst.getUniqueId().toString().equals(instanceName))
                .findFirst()
                .orElse(null);

        if (targetInstance != null) {
            Pos currentPos = fakePlayer.getPosition();
            fakePlayer.setInstance(targetInstance, currentPos).thenRun(() -> {
                LOGGER.info("FakePlayer {} moved to instance {}", modelId, instanceName);
            });
        } else {
            LOGGER.warn("Instance {} not found for FakePlayer {}", instanceName, modelId);
        }
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

        Pos newPos = new Pos(position.x, position.y, position.z, yaw, pitch);
        fakePlayer.teleport(newPos).thenRun(() -> {
            LOGGER.debug("FakePlayer {} teleported to {}", modelId, newPos);
        });
    }

    @HostAccess.Export
    public void walkTo(Value targetValue, Value options) {
        if (isWalking()) {
            stopWalking();
        }

        Vector3 target = valueToVector3(targetValue);
        if (target == null) {
            LOGGER.error("Invalid target position for walkTo: {}", targetValue);
            return;
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


    private static Vector3 valueToVector3(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isHostObject() && value.asHostObject() instanceof Vector3) {
            return value.asHostObject();
        }
        if (value.hasMembers()) {
            double x = PolyglotValueUtil.readDouble(value, "x", Double.NaN);
            double y = PolyglotValueUtil.readDouble(value, "y", Double.NaN);
            double z = PolyglotValueUtil.readDouble(value, "z", Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return null;
            }
            return new Vector3(x, y, z);
        }

        return null;
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
            Pos finalPos = new Pos(position.x, position.y, position.z, yaw, pitch);
            fakePlayer.teleport(finalPos);
            stopWalking();
            return;
        }

        Vector3 dirNorm = direction.normalize();
        Vector3 velocity = dirNorm.multiply(moveDistancePerTick);
        this.position = this.position.add(velocity);

        if (dirNorm.lengthSquared() > 0.0001f) {
            this.yaw = (float) Math.toDegrees(Math.atan2(-dirNorm.x, dirNorm.z));
        }

        Pos newPos = new Pos(position.x, position.y, position.z, yaw, pitch);
        fakePlayer.teleport(newPos);
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

            Pos currentPos = fakePlayer.getPosition();
            Pos newPos = currentPos.withView(yaw, pitch);
            fakePlayer.teleport(newPos);
        }
    }

    @HostAccess.Export
    public void setSkin(String skinUrl) {
        this.skinUrl = skinUrl != null ? skinUrl : "";
        applySkinToFakePlayer(skinUrl);
    }

    private void applySkinToFakePlayer(String skinUrl) {
        if (skinUrl == null || skinUrl.isEmpty()) {
            LOGGER.warn("Empty skin URL provided for player model {}", modelId);
            return;
        }

        try {

            LOGGER.info("Skin URL set for FakePlayer {}: {}", modelId, skinUrl);

            // TODO: implement async skin loading via PlayerSkin.fromUrl()

        } catch (Exception e) {
            LOGGER.error("Failed to set skin for FakePlayer {}", modelId, e);
        }
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
        if (animationName != null && animationName.equals(this.currentAnimation)) {
            return;
        }
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

        fakePlayer.removeFakePlayer();
        LOGGER.info("PlayerModelProxy {} removed", modelId);
    }

    @HostAccess.Export
    public void onClick(Value callback) {
        if (callback != null && callback.canExecute()) {
            this.clickCallback = callback;
        }
    }

    private void broadcastAnimation() {
        MoudPackets.S2C_PlayModelAnimationPacket packet = new MoudPackets.S2C_PlayModelAnimationPacket(modelId, currentAnimation);
        broadcast(packet);
    }

    private void stopAnimation() {
        MoudPackets.S2C_PlayModelAnimationPacket packet = new MoudPackets.S2C_PlayModelAnimationPacket(modelId, "");
        broadcast(packet);
    }

    private void broadcast(Object packet) {
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager == null) {
            return;
        }
        networkManager.broadcast(packet);
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

    public FakePlayer getFakePlayer() {
        return fakePlayer;
    }
}

