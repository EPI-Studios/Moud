package com.moud.server.movement;

import com.moud.api.collision.AABB;
import com.moud.api.math.Vector3;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsController;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.api.physics.player.PlayerState;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.physics.PhysicsService;
import com.moud.server.scripting.ScriptPlayerContextProvider;
import com.moud.server.scripting.ScriptThreadContext;
import com.moud.server.physics.primitives.PrimitiveCollisionBounds;
import com.moud.server.primitives.PrimitiveInstance;
import com.moud.server.primitives.PrimitiveServiceImpl;
import com.moud.server.zone.ZoneManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerMovementSimService {
    private static final float FIXED_DT_SECONDS = MinecraftServer.TICK_MS / 1000f;
    private static final float PLAYER_PUSH_STRENGTH = 0.35f;
    private static final int INPUT_DECAY_TICKS = 6;
    private static final double COLLISION_QUERY_EPS = 1.0e-9;
    private static final double MESH_GROUND_PROBE = 0.05;

    private static final PlayerMovementSimService INSTANCE = new PlayerMovementSimService();

    public static PlayerMovementSimService getInstance() {
        return INSTANCE;
    }

    private final PlayerPhysicsConfig defaultConfig = PlayerPhysicsConfig.predictionDefaults();
    private final ConcurrentMap<UUID, SimPlayer> players = new ConcurrentHashMap<>();
    private final AtomicBoolean handlersRegistered = new AtomicBoolean(false);
    private volatile Task tickTask;

    private PlayerMovementSimService() {
        registerEventHandlers();
        startTickTask();
    }

    public boolean isPredictionEnabled(Player player) {
        return player != null && players.containsKey(player.getUuid());
    }

    public void setPredictionMode(Player player, boolean enabled) {
        setPredictionMode(player, enabled, PlayerPhysicsControllers.DEFAULT_ID, defaultConfig);
    }

    public void setPredictionMode(
            Player player,
            boolean enabled,
            String controllerId,
            PlayerPhysicsConfig configOverride
    ) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (enabled) {
            players.computeIfAbsent(playerId, ignored -> {
                Pos pos = player.getPosition();
                PlayerState state = new PlayerState(pos.x(), pos.y(), pos.z(), 0f, 0f, 0f, player.isOnGround(), false);
                return new SimPlayer(
                        playerId,
                        state,
                        controllerId,
                        configOverride != null ? configOverride : defaultConfig,
                        new MinestomPlayerContextProvider(player)
                );
            });

            SimPlayer sim = players.get(playerId);
            if (sim != null) {
                sim.setController(controllerId);
                sim.setConfig(configOverride != null ? configOverride : defaultConfig);
                sim.setContextProvider(new MinestomPlayerContextProvider(player));
            }
        } else {
            players.remove(playerId);
        }

        ServerNetworkManager network = ServerNetworkManager.getInstance();
        if (network != null && network.isMoudClient(player)) {
            network.send(player, new MoudPackets.PhysicsModePacket(
                    enabled,
                    enabled ? controllerId : null,
                    enabled ? (configOverride != null ? configOverride : defaultConfig) : null
            ));
        }
    }

    public void flushClientMode(Player player) {
        if (player == null) {
            return;
        }
        ServerNetworkManager network = ServerNetworkManager.getInstance();
        if (network == null || !network.isMoudClient(player)) {
            return;
        }
        SimPlayer sim = players.get(player.getUuid());
        boolean enabled = sim != null;
        network.send(player, new MoudPackets.PhysicsModePacket(
                enabled,
                enabled ? sim.controllerId : null,
                enabled ? sim.config : null
        ));
    }

    public void handleInput(Player player, MoudPackets.PlayerInputPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        SimPlayer sim = players.get(player.getUuid());
        if (sim == null) {
            return;
        }
        long seq = packet.sequenceId();
        if (seq <= 0) {
            return;
        }
        sim.pendingInputs.put(seq, decodeInput(packet));
    }

    private void startTickTask() {
        if (tickTask != null) {
            return;
        }
        tickTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    private void registerEventHandlers() {
        if (!handlersRegistered.compareAndSet(false, true)) {
            return;
        }
        var handler = MinecraftServer.getGlobalEventHandler();
        handler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            SimPlayer sim = players.get(player.getUuid());
            if (sim == null) {
                return;
            }
            if (sim.suppressNextMoveEvent.getAndSet(false)) {
                return;
            }
            Pos current = player.getPosition();
            Pos incoming = event.getNewPosition();
            event.setNewPosition(new Pos(current.x(), current.y(), current.z(), incoming.yaw(), incoming.pitch()));
        });
        handler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            if (player != null) {
                players.remove(player.getUuid());
            }
        });
        handler.addListener(PlayerGameModeChangeEvent.class, event -> {
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            if (event.isCancelled()) {
                return;
            }
            if (event.getNewGameMode() == player.getGameMode()) {
                return;
            }
            if (!isPredictionEnabled(player)) {
                return;
            }
            setPredictionMode(player, false);
        });
    }

    private void tick() {
        if (players.isEmpty()) {
            return;
        }

        ServerNetworkManager network = ServerNetworkManager.getInstance();
        PhysicsService physics = PhysicsService.getInstance();
        JoltPredictionCollisionWorld joltWorld = JoltPredictionCollisionWorld.getInstance();

        List<UUID> toRemove = null;
        for (SimPlayer sim : players.values()) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(sim.playerId);
            if (player == null || !player.isOnline()) {
                if (toRemove == null) {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(sim.playerId);
                continue;
            }
            Instance instance = player.getInstance();
            if (instance == null) {
                continue;
            }

            joltWorld.syncMeshesForInstance(instance);

            PlayerInput input = sim.nextInput();
            CollisionWorld world = new ServerCollisionWorld(instance, physics);
            PlayerState nextState;
            ScriptThreadContext.setPlayer(sim.playerId, sim.contextProvider);
            try {
                PlayerState grounded = applyJoltMeshGroundProbe(joltWorld, sim, sim.state, sim.config);
                nextState = sim.controller.step(grounded, input, sim.config, world, FIXED_DT_SECONDS);

                nextState = applyJoltMeshCollision(joltWorld, sim, grounded, nextState, sim.config);
            } finally {
                ScriptThreadContext.clear();
            }

            sim.state = nextState;
            sim.lastProcessedSeq = Math.max(sim.lastProcessedSeq, input.sequenceId());

            sim.suppressNextMoveEvent.set(true);
            player.refreshPosition(new Pos(nextState.x(), nextState.y(), nextState.z(), input.yaw(), input.pitch()));

            if (network != null && network.isMoudClient(player)) {
                network.send(player, new MoudPackets.PlayerSnapshotPacket(
                        sim.lastProcessedSeq,
                        nextState.x(),
                        nextState.y(),
                        nextState.z(),
                        nextState.velX(),
                        nextState.velY(),
                        nextState.velZ(),
                        nextState.onGround()
                ));
            }

            AABB playerBox = playerAabb(nextState, sim.config);
            Vector3 vel = new Vector3(nextState.velX(), nextState.velY(), nextState.velZ());
            physics.applyPlayerPush(playerBox, vel, PLAYER_PUSH_STRENGTH);
        }

        if (toRemove != null) {
            for (UUID id : toRemove) {
                players.remove(id);
            }
        }
    }

    private static PlayerInput decodeInput(MoudPackets.PlayerInputPacket packet) {
        int bits = packet.inputBits();
        return new PlayerInput(
                packet.sequenceId(),
                (bits & InputBits.FORWARD) != 0,
                (bits & InputBits.BACKWARD) != 0,
                (bits & InputBits.LEFT) != 0,
                (bits & InputBits.RIGHT) != 0,
                (bits & InputBits.JUMP) != 0,
                (bits & InputBits.SPRINT) != 0,
                (bits & InputBits.SNEAK) != 0,
                packet.yaw(),
                packet.pitch()
        );
    }

    private static AABB playerAabb(PlayerState state, PlayerPhysicsConfig config) {
        float width = config.width() > 0 ? config.width() : 0.6f;
        float height = config.height() > 0 ? config.height() : 1.8f;
        double half = width * 0.5;
        return new AABB(
                state.x() - half,
                state.y(),
                state.z() - half,
                state.x() + half,
                state.y() + height,
                state.z() + half
        );
    }


    private static PlayerState applyJoltMeshCollision(
            JoltPredictionCollisionWorld joltWorld,
            SimPlayer sim,
            PlayerState prevState,
            PlayerState nextState,
            PlayerPhysicsConfig config
    ) {
        if (joltWorld == null || sim == null || prevState == null || nextState == null || config == null) {
            return nextState;
        }

        double moveX = nextState.x() - prevState.x();
        double moveY = nextState.y() - prevState.y();
        double moveZ = nextState.z() - prevState.z();

        var character = sim.ensureJoltCharacter(joltWorld, config);
        if (character == null || !joltWorld.isInitialized()) {
            return nextState;
        }

        boolean allowStep = prevState.onGround() && nextState.velY() <= 0.02f;
        float stepHeight = config.stepHeight();
        float height = config.height();

        JoltPredictionCollisionWorld.MoveResult result = joltWorld.moveCharacter(
                character,
                prevState.x(), prevState.y(), prevState.z(),
                moveX, moveY, moveZ,
                FIXED_DT_SECONDS,
                height,
                stepHeight,
                allowStep
        );

        double newX = prevState.x() + result.dx();
        double newY = prevState.y() + result.dy();
        double newZ = prevState.z() + result.dz();

        boolean blockedX = axisBlocked(moveX, result.dx());
        boolean blockedY = axisBlocked(moveY, result.dy());
        boolean blockedZ = axisBlocked(moveZ, result.dz());

        float velX = nextState.velX();
        float velY = nextState.velY();
        float velZ = nextState.velZ();

        if (blockedX) {
            velX = 0;
        }
        if (blockedY) {
            velY = 0;
        }
        if (blockedZ) {
            velZ = 0;
        }

        boolean onGround = nextState.onGround();
        boolean landedOnMesh = !onGround && moveY < 0 && blockedY;
        if (landedOnMesh) {
            onGround = true;
        }
        if (velY > 0.02f) {
            onGround = false;
        }
        if (onGround && velY < 0.0f) {
            velY = 0.0f;
        }
        boolean horizontalCollision = nextState.collidingHorizontally() || blockedX || blockedZ || result.collidingHorizontally();

        return new PlayerState(
                newX, newY, newZ,
                velX, velY, velZ,
                onGround,
                horizontalCollision
        );
    }

    private static PlayerState applyJoltMeshGroundProbe(
            JoltPredictionCollisionWorld joltWorld,
            SimPlayer sim,
            PlayerState state,
            PlayerPhysicsConfig config
    ) {
        if (joltWorld == null || sim == null || state == null || config == null) {
            return state;
        }
        if (state.onGround()) {
            return state;
        }
        if (state.velY() > 0.02f) {
            return state;
        }

        var character = sim.ensureJoltCharacter(joltWorld, config);
        if (character == null || !joltWorld.isInitialized()) {
            return state;
        }

        JoltPredictionCollisionWorld.MoveResult result = joltWorld.moveCharacter(
                character,
                state.x(), state.y(), state.z(),
                0.0, -MESH_GROUND_PROBE, 0.0,
                FIXED_DT_SECONDS,
                config.height(),
                0.0f,
                false
        );
        boolean grounded = axisBlocked(-MESH_GROUND_PROBE, result.dy());
        if (!grounded && result.onGround()) {
            grounded = Math.abs(result.dx()) > 1.0e-6 || Math.abs(result.dz()) > 1.0e-6;
        }

        if (!grounded) {
            return state;
        }

        float velY = state.velY();
        if (velY < 0.0f) {
            velY = 0.0f;
        }

        return new PlayerState(
                state.x(), state.y(), state.z(),
                state.velX(), velY, state.velZ(),
                true,
                state.collidingHorizontally()
        );
    }

    private static boolean axisBlocked(double requested, double allowed) {
        if (Math.abs(requested) <= 1e-9) {
            return false;
        }
        double diff = allowed - requested;
        if (Math.abs(diff) <= 1e-6) {
            return false;
        }
        if (allowed * requested < -1e-9) {
            return true;
        }
        return Math.signum(allowed) == Math.signum(requested) && Math.abs(allowed) < Math.abs(requested) - 1e-6;
    }

    private static final class ServerCollisionWorld implements CollisionWorld {
        private final Instance instance;
        private final PhysicsService physicsService;

        private ServerCollisionWorld(Instance instance, PhysicsService physicsService) {
            this.instance = instance;
            this.physicsService = physicsService;
        }

        @Override
        public List<AABB> getCollisions(AABB query) {
            if (instance == null || query == null) {
                return List.of();
            }

            int minX = (int) Math.floor(query.minX());
            int minY = (int) Math.floor(query.minY());
            int minZ = (int) Math.floor(query.minZ());

            int maxX = (int) Math.floor(query.maxX() - COLLISION_QUERY_EPS);
            int maxY = (int) Math.floor(query.maxY() - COLLISION_QUERY_EPS);
            int maxZ = (int) Math.floor(query.maxZ() - COLLISION_QUERY_EPS);

            List<AABB> result = new ArrayList<>();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = instance.getBlock(x, y, z);
                        if (block.isAir() || block.isLiquid()) {
                            continue;
                        }
                        Shape shape = block.registry().collisionShape();
                        if (shape == null) {
                            continue;
                        }
                        Point relStart = shape.relativeStart();
                        Point relEnd = shape.relativeEnd();
                        if (relStart == null || relEnd == null) {
                            continue;
                        }
                        if (relStart.x() == relEnd.x() || relStart.y() == relEnd.y() || relStart.z() == relEnd.z()) {
                            continue;
                        }
                        result.add(new AABB(
                                x + relStart.x(),
                                y + relStart.y(),
                                z + relStart.z(),
                                x + relEnd.x(),
                                y + relEnd.y(),
                                z + relEnd.z()
                        ));
                    }
                }
            }

            PrimitiveServiceImpl primitiveService = PrimitiveServiceImpl.getInstance();
            for (PrimitiveInstance primitive : primitiveService.getPrimitiveInstances()) {
                AABB bounds = PrimitiveCollisionBounds.computeAabb(primitive);
                if (bounds == null || !bounds.intersects(query)) {
                    continue;
                }
                result.add(bounds);
            }

            if (physicsService != null) {
                result.addAll(physicsService.getPlayerBlockingColliders(instance, query));
            }

            if (result.size() <= 1) {
                return result;
            }
            result.sort(Comparator.comparingDouble(AABB::minY));
            return result;
        }
    }

    private static final class SimPlayer {
        private final UUID playerId;
        private PlayerPhysicsConfig config;
        private ScriptPlayerContextProvider contextProvider;
        private String controllerId;
        private PlayerPhysicsController controller;
        private final NavigableMap<Long, PlayerInput> pendingInputs;
        private final AtomicBoolean suppressNextMoveEvent = new AtomicBoolean(false);
        private PlayerState state;
        private PlayerInput lastInput;
        private long lastProcessedSeq;
        private int emptyInputTicks;
        private com.github.stephengold.joltjni.CharacterVirtual joltCharacter;
        private float joltWidth = -1f;
        private float joltHeight = -1f;

        private SimPlayer(
                UUID playerId,
                PlayerState initialState,
                String controllerId,
                PlayerPhysicsConfig config,
                ScriptPlayerContextProvider contextProvider
        ) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.state = Objects.requireNonNull(initialState, "initialState");
            this.config = Objects.requireNonNull(config, "config");
            this.contextProvider = contextProvider;
            this.controllerId = controllerId != null ? controllerId : PlayerPhysicsControllers.DEFAULT_ID;
            this.controller = PlayerPhysicsControllers.get(this.controllerId);
            this.pendingInputs = new ConcurrentSkipListMap<>();
            this.lastInput = new PlayerInput(0L, false, false, false, false, false, false, false, 0.0f, 0.0f);
        }

        private com.github.stephengold.joltjni.CharacterVirtual ensureJoltCharacter(
                JoltPredictionCollisionWorld joltWorld,
                PlayerPhysicsConfig config
        ) {
            if (joltWorld == null || config == null) {
                return null;
            }
            float width = config.width() > 0 ? config.width() : 0.6f;
            float height = config.height() > 0 ? config.height() : 1.8f;
            if (joltCharacter == null || Math.abs(width - joltWidth) > 1.0e-6f || Math.abs(height - joltHeight) > 1.0e-6f) {
                try {
                    joltCharacter = joltWorld.createCharacter(width, height);
                    joltWidth = width;
                    joltHeight = height;
                } catch (Throwable t) {
                    joltCharacter = null;
                    return null;
                }
            }
            return joltCharacter;
        }

        private void setController(String controllerId) {
            String resolved = controllerId != null ? controllerId : PlayerPhysicsControllers.DEFAULT_ID;
            if (resolved.equals(this.controllerId)) {
                return;
            }
            this.controllerId = resolved;
            this.controller = PlayerPhysicsControllers.get(resolved);
        }

        private void setConfig(PlayerPhysicsConfig config) {
            this.config = Objects.requireNonNull(config, "config");
        }

        private void setContextProvider(ScriptPlayerContextProvider contextProvider) {
            this.contextProvider = contextProvider;
        }

        private PlayerInput nextInput() {
            PlayerInput input = pollNext();
            if (input != null) {
                emptyInputTicks = 0;
                lastInput = input;
                return input;
            }

            emptyInputTicks++;
            if (emptyInputTicks <= INPUT_DECAY_TICKS) {
                return lastInput;
            }

            return new PlayerInput(
                    lastInput.sequenceId(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    lastInput.sprint(),
                    lastInput.sneak(),
                    lastInput.yaw(),
                    lastInput.pitch()
            );
        }

        private PlayerInput pollNext() {
            while (true) {
                var entry = pendingInputs.pollFirstEntry();
                if (entry == null) {
                    return null;
                }
                if (entry.getKey() <= lastProcessedSeq) {
                    continue;
                }
                lastProcessedSeq = entry.getKey();
                return entry.getValue();
            }
        }
    }

    private static final class MinestomPlayerContextProvider implements ScriptPlayerContextProvider {
        private final Player player;

        private MinestomPlayerContextProvider(Player player) {
            this.player = player;
        }

        @Override
        public boolean hasItem(String itemId) {
            return false;
        }

        @Override
        public float getHealth() {
            if (player == null) {
                return 20f;
            }
            try {
                return player.getHealth();
            } catch (Exception ignored) {
                return 20f;
            }
        }

        @Override
        public boolean hasEffect(String effectId) {
            return false;
        }

        @Override
        public Object getData(String key) {
            return null;
        }

        @Override
        public String getBlock(double x, double y, double z) {
            if (player == null) {
                return "minecraft:air";
            }
            Instance instance = player.getInstance();
            if (instance == null) {
                return "minecraft:air";
            }
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            try {
                return instance.getBlock(bx, by, bz).name();
            } catch (Exception ignored) {
                return "minecraft:air";
            }
        }

        @Override
        public boolean isInZone(double x, double y, double z, String zoneId) {
            if (zoneId == null || zoneId.isBlank()) {
                return false;
            }
            com.moud.server.MoudEngine engine = com.moud.server.MoudEngine.getInstance();
            if (engine == null) {
                return false;
            }
            ZoneManager zones = engine.getZoneManager();
            if (zones == null) {
                return false;
            }
            return zones.isInZone(x, y, z, zoneId);
        }
    }

    public static final class InputBits {
        public static final int FORWARD = 1 << 0;
        public static final int BACKWARD = 1 << 1;
        public static final int LEFT = 1 << 2;
        public static final int RIGHT = 1 << 3;
        public static final int JUMP = 1 << 4;
        public static final int SNEAK = 1 << 5;
        public static final int SPRINT = 1 << 6;

        private InputBits() {
        }
    }
}
