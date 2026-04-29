package com.moud.client.fabric.render.scene.subrender.particle;

import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ParticleRenderer {
    private static final long CONFIG_TTL_NANOS = 250_000_000L;
    private static final double DEFAULT_CULL_DISTANCE_SQ = 96.0 * 96.0;

    private final Map<Long, EmitterState> emitters = new HashMap<>();
    private final ParticleSpawner spawner = new ParticleSpawner();
    private final ParticleSimulation simulation = new ParticleSimulation();
    private final ParticleDrawer drawer = new ParticleDrawer();
    private final Pose scratchPose = new Pose();
    private long lastNanoTime;

    public ParticleRenderer() {
        ParticleApi.bind(this);
    }

    public EmitterState state(long nodeId) {
        return emitters.get(nodeId);
    }

    public void render(java.util.List<SceneSnapshot.NodeSnapshot> nodes,
                       Function<Long, Pose> poseResolver,
                       VertexConsumerProvider.Immediate consumers,
                       MatrixStack matrices,
                       Vec3d camPos,
                       Camera camera,
                       Frustum frustum,
                       MinecraftClient client,
                       float tickDelta) {
        if (nodes == null || nodes.isEmpty() || client == null || client.world == null) return;

        long now = System.nanoTime();
        float dt = lastNanoTime == 0L ? 1.0f / 60.0f : Math.min(0.066f, (now - lastNanoTime) / 1.0e9f);
        lastNanoTime = now;

        ClientRapierPhysics physics = ClientRapierPhysics.get();
        if (physics != null && physics.isAvailable()) {
            try { physics.syncSceneIfNeeded(); } catch (Throwable ignored) {}
        }

        Set<Long> live = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || !"Particle3D".equals(node.type())) continue;
            live.add(node.nodeId());
            Pose world = poseResolver.apply(node.nodeId());
            if (world == null) continue;

            EmitterState state = emitters.computeIfAbsent(node.nodeId(), id -> new EmitterState());
            EmitterConfig config = resolveConfig(state, node, now);
            ensureInitialized(state, config, world);

            double ex = state.hasPoseOffset ? state.poseOffsetX : world.pos.x;
            double ey = state.hasPoseOffset ? state.poseOffsetY : world.pos.y;
            double ez = state.hasPoseOffset ? state.poseOffsetZ : world.pos.z;

            double cdx = ex - camPos.x;
            double cdy = ey - camPos.y;
            double cdz = ez - camPos.z;
            double distSq = cdx * cdx + cdy * cdy + cdz * cdz;
            double cullSq = DEFAULT_CULL_DISTANCE_SQ;
            if (distSq > cullSq) {
                state.particles.clear();
                state.pendingAdd.clear();
                state.spawnAccumulator = 0f;
                continue;
            }

            if (frustum != null && !isEmitterVisible(frustum, ex, ey, ez, config.cullRadius, state.particles.size())) {
                state.particles.clear();
                state.pendingAdd.clear();
                state.spawnAccumulator = 0f;
                continue;
            }

            float lodDist = config.lodDistance;
            if (lodDist > 0.1f && distSq > lodDist * lodDist) {
                state.lowDetail = true;
                state.lodSizeScale = config.lodSizeScale;
            } else {
                state.lowDetail = false;
                state.lodSizeScale = 1f;
            }

            if (state.emittingOverride != null) config.emitting = state.emittingOverride;

            Pose effectivePose = state.hasPoseOffset ? overridePose(world, ex, ey, ez) : world;

            float dx = 0f, dy = 0f, dz = 0f;
            boolean moved = false;
            if (state.hasLastPos) {
                dx = (float) (ex - state.lastEmitterX);
                dy = (float) (ey - state.lastEmitterY);
                dz = (float) (ez - state.lastEmitterZ);
                moved = dx != 0f || dy != 0f || dz != 0f;
            }
            if (config.localSpace && moved) {
                simulation.applyLocalSpaceDrift(state, dx, dy, dz);
            }
            if (dt > 0f) {
                state.parentVelX = dx / dt;
                state.parentVelY = dy / dt;
                state.parentVelZ = dz / dt;
            }

            simulation.tick(state, config, dt, client.world, physics);

            float effectiveRate = state.rateOverride != null ? state.rateOverride : config.rate;
            float effectiveLifetime = state.lifetimeOverride != null ? state.lifetimeOverride : config.lifetime;
            if (state.lowDetail) effectiveRate *= config.lodRateScale;

            float savedRate = config.rate;
            float savedLifetime = config.lifetime;
            config.rate = effectiveRate;
            config.lifetime = effectiveLifetime;

            if (config.oneShot) {
                if (!state.oneShotFired) {
                    int count = config.burstCount > 0 ? config.burstCount : Math.max(1, (int) effectiveRate);
                    spawner.spawnBurst(state, config, effectivePose, count);
                    applyInheritVelocity(state, config, count);
                    state.oneShotFired = true;
                }
            } else {
                int spawned = spawner.spawnContinuous(state, config, effectivePose, dt);
                applyInheritVelocity(state, config, spawned);
            }

            while (!state.pendingBursts.isEmpty()) {
                int count = state.pendingBursts.poll();
                spawner.spawnBurst(state, config, effectivePose, count);
                applyInheritVelocity(state, config, count);
            }
            if (state.forceEmitOnce) {
                state.forceEmitOnce = false;
                int count = config.burstCount > 0 ? config.burstCount : Math.max(1, (int) effectiveRate);
                spawner.spawnBurst(state, config, effectivePose, count);
                applyInheritVelocity(state, config, count);
            }

            config.rate = savedRate;
            config.lifetime = savedLifetime;

            state.lastEmitterX = ex;
            state.lastEmitterY = ey;
            state.lastEmitterZ = ez;
            state.hasLastPos = true;

            drawer.draw(state, config, consumers, camPos, camera, client);

            if (state.restartRequested) {
                state.particles.clear();
                state.pendingAdd.clear();
                state.pendingBursts.clear();
                state.oneShotFired = false;
                state.spawnAccumulator = 0f;
                state.restartRequested = false;
                state.rng = new SeededRng(resolveSeed(config, node.nodeId()));
                state.initialized = false;
            }
        }

        emitters.keySet().removeIf(id -> !live.contains(id));
    }

    private static boolean isEmitterVisible(Frustum frustum, double ex, double ey, double ez, float radius, int particleCount) {
        float r = Math.max(radius, particleCount > 0 ? 2f : 0.5f);
        Box box = new Box(ex - r, ey - r, ez - r, ex + r, ey + r, ez + r);
        try {
            return frustum.isVisible(box);
        } catch (Exception ignored) {
            return true;
        }
    }

    private Pose overridePose(Pose base, double x, double y, double z) {
        Pose.copy(base, scratchPose);
        scratchPose.pos.set((float) x, (float) y, (float) z);
        return scratchPose;
    }

    private static void applyInheritVelocity(EmitterState state, EmitterConfig c, int newlySpawned) {
        if (c.inheritVelocity <= 0f || newlySpawned <= 0) return;
        int size = state.particles.size();
        int start = Math.max(0, size - newlySpawned);
        float k = c.inheritVelocity;
        float pvx = (float) state.parentVelX * k;
        float pvy = (float) state.parentVelY * k;
        float pvz = (float) state.parentVelZ * k;
        for (int i = start; i < size; i++) {
            Particle p = state.particles.get(i);
            p.vx += pvx;
            p.vy += pvy;
            p.vz += pvz;
        }
    }

    private void ensureInitialized(EmitterState state, EmitterConfig config, Pose world) {
        if (state.initialized && state.seedVersion == config.seed) return;
        state.rng = new SeededRng(resolveSeed(config, System.identityHashCode(state)));
        state.seedVersion = config.seed;
        state.initialized = true;
        if (config.prewarm > 0f) {
            float step = 1.0f / 30.0f;
            float elapsed = 0f;
            while (elapsed < config.prewarm) {
                simulation.tick(state, config, step, null, null);
                spawner.spawnContinuous(state, config, world, step);
                elapsed += step;
            }
        }
    }

    private static long resolveSeed(EmitterConfig c, long fallback) {
        return c.seed != 0L ? c.seed : fallback ^ System.nanoTime();
    }

    private static EmitterConfig resolveConfig(EmitterState state, SceneSnapshot.NodeSnapshot node, long nowNanos) {
        int identity = System.identityHashCode(node.properties());
        if (state.cachedConfig != null
                && identity == state.cachedPropsIdentity
                && nowNanos - state.cachedConfigNanos < CONFIG_TTL_NANOS) {
            return state.cachedConfig;
        }
        EmitterConfig config = EmitterConfig.parse(node);
        state.cachedConfig = config;
        state.cachedConfigNanos = nowNanos;
        state.cachedPropsIdentity = identity;
        return config;
    }
}
