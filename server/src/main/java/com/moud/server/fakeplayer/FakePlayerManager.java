package com.moud.server.fakeplayer;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.physics.PhysicsService;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.event.player.PlayerSpawnEvent;
import com.github.stephengold.joltjni.enumerate.EActivation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public final class FakePlayerManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(FakePlayerManager.class);
    private static FakePlayerManager INSTANCE;

    public static FakePlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FakePlayerManager();
        }
        return INSTANCE;
    }

    private final Map<Long, FakePlayerState> players = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);
    private ServerNetworkManager network;
    private Instance instance;
    private PhysicsService physicsService;

    private FakePlayerManager() {
    }

    public void initialize(ServerNetworkManager networkManager, PhysicsService physicsService) {
        this.network = networkManager;
        this.instance = InstanceManager.getInstance().getDefaultInstance();
        this.physicsService = physicsService;
        // tick at 20
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(50, TimeUnit.MILLISECONDS.toChronoUnit())
                .schedule();
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> syncToPlayer(event.getPlayer()));
        LOGGER.info("Initialized FakePlayerManager");
    }

    public FakePlayerState spawn(MoudPackets.FakePlayerDescriptor descriptor) {
        long id = descriptor.id() > 0 ? descriptor.id() : idSeq.getAndIncrement();
        FakePlayerState state = new FakePlayerState(id, descriptor);
        state.attachPhysics(physicsService);
        players.put(id, state);
        broadcastCreate(state);
        return state;
    }

    public void remove(long id) {
        FakePlayerState state = players.remove(id);
        if (state != null) {
            state.detachPhysics(physicsService);
        }
        if (network != null) {
            network.broadcast(new MoudPackets.S2C_RemoveFakePlayer(id));
        }
    }

    public void update(MoudPackets.FakePlayerDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        long id = descriptor.id();
        if (id <= 0) {
            spawn(descriptor);
            return;
        }
        FakePlayerState state = players.get(id);
        if (state == null) {
            spawn(descriptor);
            return;
        }
        boolean changed = state.apply(descriptor, physicsService);
        if (changed && network != null) {
            network.broadcast(new MoudPackets.S2C_UpdateFakePlayer(
                    state.id,
                    state.position,
                    state.rotation,
                    state.sneaking,
                    state.sprinting,
                    state.swinging,
                    state.usingItem
            ));
        }
    }

    public void syncToPlayer(net.minestom.server.entity.Player player) {
        if (network == null) return;
        for (FakePlayerState state : players.values()) {
            network.send(player, new MoudPackets.S2C_CreateFakePlayer(state.toDescriptor()));
            if (state.currentAnimation != null && !state.currentAnimation.isBlank()) {
                network.send(player, new MoudPackets.S2C_PlayModelAnimationPacket(state.id, state.currentAnimation));
            }
        }
    }

    private void broadcastCreate(FakePlayerState state) {
        if (network == null) return;
        network.broadcast(new MoudPackets.S2C_CreateFakePlayer(state.toDescriptor()));
    }

    private void tick() {
        if (players.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (FakePlayerState state : players.values()) {
            boolean changed = state.tick(now);
            if (changed && network != null) {
                network.broadcast(new MoudPackets.S2C_UpdateFakePlayer(
                        state.id,
                        state.position,
                        state.rotation,
                        state.sneaking,
                        state.sprinting,
                        state.swinging,
                        state.usingItem
                ));
            }
        }
    }

    private void broadcastUpdate(FakePlayerState state) {
        if (network == null || state == null) return;
        network.broadcast(new MoudPackets.S2C_UpdateFakePlayer(
                state.id,
                state.position,
                state.rotation,
                state.sneaking,
                state.sprinting,
                state.swinging,
                state.usingItem
        ));
    }

    public static final class FakePlayerState {
        private final long id;
        private String label;
        private String skinUrl;
        private Vector3 position;
        private Quaternion rotation;
        private double width;
        private double height;
        private boolean physicsEnabled;
        private boolean sneaking;
        private boolean sprinting;
        private boolean swinging;
        private boolean usingItem;
        private String currentAnimation;
        private final List<Vector3> path = new ArrayList<>();
        private double pathSpeed;
        private boolean pathLoop;
        private boolean pathPingPong;
        private int pathIndex = 0;
        private boolean pathForward = true;
        private Body body;
        private long lastTickMs = System.currentTimeMillis();
        private long lastAnimationEndMs = 0L;

        private FakePlayerState(long id, MoudPackets.FakePlayerDescriptor descriptor) {
            this.id = id;
            this.label = descriptor.label();
            this.skinUrl = descriptor.skinUrl();
            this.position = descriptor.position();
            this.rotation = descriptor.rotation() != null ? descriptor.rotation() : Quaternion.identity();
            this.width = descriptor.width() > 0 ? descriptor.width() : 0.6;
            this.height = descriptor.height() > 0 ? descriptor.height() : 1.8;
            this.physicsEnabled = descriptor.physicsEnabled();
            this.sneaking = descriptor.sneaking();
            this.sprinting = descriptor.sprinting();
            this.swinging = descriptor.swinging();
            this.usingItem = descriptor.usingItem();
            this.currentAnimation = null;
            if (descriptor.path() != null) {
                descriptor.path().forEach(wp -> {
                    if (wp != null && wp.position() != null) {
                        path.add(wp.position());
                    }
                });
            }
            this.pathSpeed = descriptor.pathSpeed() > 0 ? descriptor.pathSpeed() : 1.5;
            this.pathLoop = descriptor.pathLoop();
            this.pathPingPong = descriptor.pathPingPong();
            faceNextTarget();
        }

        private void attachPhysics(PhysicsService physicsService) {
            if (!physicsEnabled || physicsService == null) {
                return;
            }
            try {
                BoxShape shape = new BoxShape((float) (width / 2.0), (float) (height / 2.0), (float) (width / 2.0));
                body = physicsService.getBodyInterface().createBody(
                        new com.github.stephengold.joltjni.BodyCreationSettings()
                                .setShape(shape)
                                .setPosition(new com.github.stephengold.joltjni.RVec3(position.x, position.y, position.z))
                                .setMotionType(com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic)
                                .setObjectLayer(com.moud.server.physics.PhysicsService.LAYER_DYNAMIC)
                );
                physicsService.getBodyInterface().addBody(body, com.github.stephengold.joltjni.enumerate.EActivation.Activate);
            } catch (Exception e) {
                LOGGER.warn("Failed to attach physics for fake player {}", id, e);
            }
        }

        private void detachPhysics(PhysicsService physicsService) {
            if (body != null && physicsService != null) {
                try {
                    physicsService.getBodyInterface().removeBody(body.getId());
                    physicsService.getBodyInterface().destroyBody(body.getId());
                } catch (Exception ignored) {
                }
                body = null;
            }
        }

        private boolean tick(long nowMs) {
            boolean changed = false;
            float deltaSec = 0.05f;
            if (lastTickMs > 0) {
                deltaSec = Math.max(0.01f, (nowMs - lastTickMs) / 1000f);
            }
            lastTickMs = nowMs;
            if (body != null && path.isEmpty()) {
                RVec3 pos = body.getPosition();
                double bx = pos.getX() instanceof Number nX ? nX.doubleValue() : 0.0;
                double by = pos.getY() instanceof Number nY ? nY.doubleValue() : 0.0;
                double bz = pos.getZ() instanceof Number nZ ? nZ.doubleValue() : 0.0;
                position = new Vector3(bx, by, bz);
                changed = true;
            }
            if (!path.isEmpty() && pathSpeed > 0) {
                Vector3 target = path.get(pathIndex);
                Vector3 delta = target.subtract(position);
                double distSq = delta.x * delta.x + delta.y * delta.y + delta.z * delta.z;
                double step = pathSpeed * deltaSec;
                if (distSq <= step * step) {
                    position = target;
                    advancePathIndex();
                    faceNextTarget();
                    changed = true;
                } else {
                    double dist = Math.sqrt(distSq);
                    Vector3 dir = new Vector3(delta.x / dist, delta.y / dist, delta.z / dist);
                    position = new Vector3(
                            position.x + dir.x * step,
                            position.y + dir.y * step,
                            position.z + dir.z * step
                    );
                }
                // face target direction
                double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
                this.rotation = Quaternion.fromEuler(0f, (float) yaw, 0f);
                changed = true;
                if (body != null) {
                    body.setPositionAndRotationInternal(new RVec3(position.x, position.y, position.z),
                            new Quat(rotation.x, rotation.y, rotation.z, rotation.w)
                    );
                }
            }
            // simple ground clamp if physics enabled
            if (physicsEnabled) {
                double groundY = 0.0;
                position = new Vector3(position.x, Math.max(position.y, groundY), position.z);
            }
            return changed;
        }

        private void advancePathIndex() {
            if (path.isEmpty()) {
                return;
            }
            if (pathPingPong) {
                if (pathForward) {
                    if (pathIndex >= path.size() - 1) {
                        pathForward = false;
                        pathIndex = Math.max(0, path.size() - 2);
                    } else {
                        pathIndex++;
                    }
                } else {
                    if (pathIndex <= 0) {
                        pathForward = true;
                        pathIndex = Math.min(path.size() - 1, 1);
                    } else {
                        pathIndex--;
                    }
                }
            } else {
                pathIndex++;
                if (pathIndex >= path.size()) {
                    pathIndex = pathLoop ? 0 : path.size() - 1;
                }
            }
        }

        public MoudPackets.FakePlayerDescriptor toDescriptor() {
            List<MoudPackets.FakePlayerWaypoint> wps = new ArrayList<>();
            for (Vector3 v : path) {
                wps.add(new MoudPackets.FakePlayerWaypoint(v));
            }
            return new MoudPackets.FakePlayerDescriptor(
                    id,
                    label,
                    skinUrl,
                    position,
                    rotation,
                    width,
                    height,
                    physicsEnabled,
                    sneaking,
                    sprinting,
                    swinging,
                    usingItem,
                    wps,
                    pathSpeed,
                    pathLoop,
                    pathPingPong
            );
        }

        public void updatePose(boolean sneaking, boolean sprinting, boolean swinging, boolean usingItem) {
            this.sneaking = sneaking;
            this.sprinting = sprinting;
            this.swinging = swinging;
            this.usingItem = usingItem;
        }

        public void updatePath(List<MoudPackets.FakePlayerWaypoint> wps, double speed, boolean loop, boolean pingPong) {
            path.clear();
            if (wps != null) {
                for (MoudPackets.FakePlayerWaypoint wp : wps) {
                    if (wp != null && wp.position() != null) {
                        path.add(wp.position());
                    }
                }
            }
            this.pathSpeed = speed;
            this.pathLoop = loop;
            this.pathPingPong = pingPong;
            this.pathIndex = 0;
            this.pathForward = true;
            faceNextTarget();
            if (body != null) {
                body.setLinearVelocity(new com.github.stephengold.joltjni.Vec3(0, 0, 0));
            }
        }

        public Long getId() {
            return id;
        }

        private boolean apply(MoudPackets.FakePlayerDescriptor descriptor, PhysicsService physicsService) {
            boolean changed = false;

            if (!java.util.Objects.equals(label, descriptor.label())) {
                label = descriptor.label();
            }
            if (!java.util.Objects.equals(skinUrl, descriptor.skinUrl())) {
                skinUrl = descriptor.skinUrl();
            }

            Vector3 newPos = descriptor.position() != null ? descriptor.position() : position;
            Quaternion newRot = descriptor.rotation() != null ? descriptor.rotation() : rotation;
            if (!newPos.equals(position) || !newRot.equals(rotation)) {
                position = newPos;
                rotation = newRot;
                if (body != null && physicsService != null) {
                    BodyInterface bi = physicsService.getBodyInterface();
                    bi.setPositionAndRotation(
                            body.getId(),
                            new RVec3(position.x, position.y, position.z),
                            new Quat(rotation.x, rotation.y, rotation.z, rotation.w),
                            EActivation.Activate
                    );
                }
                changed = true;
            }

            boolean desiredPhysics = descriptor.physicsEnabled();
            if (desiredPhysics != physicsEnabled || descriptor.width() != width || descriptor.height() != height) {
                detachPhysics(physicsService);
                physicsEnabled = desiredPhysics;
                width = descriptor.width() > 0 ? descriptor.width() : width;
                height = descriptor.height() > 0 ? descriptor.height() : height;
                attachPhysics(physicsService);
                changed = true;
            }

            if (descriptor.sneaking() != sneaking || descriptor.sprinting() != sprinting
                    || descriptor.swinging() != swinging || descriptor.usingItem() != usingItem) {
                updatePose(descriptor.sneaking(), descriptor.sprinting(), descriptor.swinging(), descriptor.usingItem());
                changed = true;
            }

            updatePath(descriptor.path(), descriptor.pathSpeed(), descriptor.pathLoop(), descriptor.pathPingPong());
            changed = true;

            return changed;
        }

        private void faceNextTarget() {
            if (path.isEmpty()) {
                return;
            }
            Vector3 target = path.get(pathIndex);
            Vector3 delta = target.subtract(position);
            double distSq = delta.x * delta.x + delta.z * delta.z;
            if (distSq < 1e-6) {
                return;
            }
            double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
            this.rotation = Quaternion.fromEuler(0f, (float) yaw, 0f);
        }
    }

    public void updatePose(long id, boolean sneaking, boolean sprinting, boolean swinging, boolean usingItem) {
        FakePlayerState state = players.get(id);
        if (state != null) {
            state.updatePose(sneaking, sprinting, swinging, usingItem);
        }
    }

    public void teleport(long id, Vector3 position) {
        FakePlayerState state = players.get(id);
        if (state == null || position == null) return;
        state.position = position;
        if (state.body != null && physicsService != null) {
            physicsService.getBodyInterface().setPositionAndRotation(
                    state.body.getId(),
                    new RVec3(position.x, position.y, position.z),
                    new Quat(state.rotation.x, state.rotation.y, state.rotation.z, state.rotation.w),
                    EActivation.Activate
            );
        }
        broadcastUpdate(state);
    }

    public void setRotation(long id, Quaternion rotation) {
        FakePlayerState state = players.get(id);
        if (state == null || rotation == null) return;
        state.rotation = rotation;
        if (state.body != null && physicsService != null) {
            physicsService.getBodyInterface().setPositionAndRotation(
                    state.body.getId(),
                    new RVec3(state.position.x, state.position.y, state.position.z),
                    new Quat(rotation.x, rotation.y, rotation.z, rotation.w),
                    EActivation.Activate
            );
        }
        broadcastUpdate(state);
    }

    public void setStateFlag(long id, String key, boolean value) {
        FakePlayerState state = players.get(id);
        if (state == null || key == null) return;
        switch (key) {
            case "sneaking" -> state.sneaking = value;
            case "sprinting" -> state.sprinting = value;
            case "swinging" -> state.swinging = value;
            case "usingItem" -> state.usingItem = value;
            default -> { return; }
        }
        state.updatePose(state.sneaking, state.sprinting, state.swinging, state.usingItem);
        broadcastUpdate(state);
    }

        public void updatePath(long id, List<MoudPackets.FakePlayerWaypoint> waypoints, double speed, boolean loop, boolean pingPong) {
            FakePlayerState state = players.get(id);
            if (state != null) {
            state.updatePath(waypoints, speed, loop, pingPong);
        }
    }

    public void playAnimation(long id, String animationId, int durationMs) {
        FakePlayerState state = players.get(id);
        if (state == null || network == null) {
            return;
        }
        if (animationId == null || animationId.isBlank()) {
            animationId = "moud:idle";
        }
        long now = System.currentTimeMillis();
        if (animationId.equals(state.currentAnimation)) {
            if (durationMs == 0 || now < state.lastAnimationEndMs - 25) {
                return;
            }
        }
        state.currentAnimation = animationId;
        int durationTicks = Math.max(0, durationMs / 50);
        state.lastAnimationEndMs = durationMs > 0 ? now + durationMs : Long.MAX_VALUE;
        if (durationTicks > 0) {
            network.broadcast(new MoudPackets.S2C_PlayModelAnimationWithFadePacket(id, animationId, durationTicks));
        } else {
            // duration 0 mean loop
            network.broadcast(new MoudPackets.S2C_PlayModelAnimationWithFadePacket(id, animationId, 0));
        }
    }
}
