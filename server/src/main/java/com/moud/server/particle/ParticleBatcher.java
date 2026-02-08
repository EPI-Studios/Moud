package com.moud.server.particle;

import com.moud.api.particle.ColorKeyframe;
import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ScalarKeyframe;
import com.moud.api.particle.SortHint;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParticleBatcher {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ParticleBatcher.class);
    private static final int MAX_QUEUE_PER_TICK = 4096;
    private static final float MAX_LIFETIME_SECONDS = 120f;
    private static final float MAX_SIZE = 64f;
    private static final int MAX_KEYFRAMES = 16;

    private final ServerNetworkManager networkManager;
    private final List<ParticleDescriptor> queue = new ArrayList<>();
    private int droppedThisTick = 0;

    public ParticleBatcher(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public synchronized boolean enqueue(ParticleDescriptor descriptor) {
        if (!validate(descriptor)) {
            LOGGER.warn("Dropping particle descriptor due to validation failure: texture={} lifetime={}", descriptor.texture(), descriptor.lifetimeSeconds());
            return false;
        }
        if (queue.size() >= MAX_QUEUE_PER_TICK) {
            droppedThisTick++;
            return false;
        }
        queue.add(descriptor);
        return true;
    }

    public synchronized void flush() {
        if (queue.isEmpty()) {
            droppedThisTick = 0;
            return;
        }
        List<ParticleDescriptor> batch = new ArrayList<>(queue);
        queue.clear();

        if (droppedThisTick > 0) {
            LOGGER.warn(LogContext.builder()
                    .put("dropped", droppedThisTick)
                    .build(), "Particle batch dropped {} descriptors due to rate limits", droppedThisTick);
            droppedThisTick = 0;
        }

        var packet = new com.moud.network.MoudPackets.ParticleBatchPacket(Collections.unmodifiableList(batch));
        LOGGER.info(LogContext.builder().put("count", batch.size()).build(), "Flushing particle batch to clients");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (networkManager.isMoudClient(player)) {
                LOGGER.info(LogContext.builder().put("player", player.getUsername()).build(), "Sending particle batch to {}", player.getUsername());
                networkManager.send(player, packet);
            }
        }
    }

    private boolean validate(ParticleDescriptor descriptor) {
        if (descriptor.texture() == null || descriptor.texture().isBlank()) {
            return false;
        }
        if (descriptor.lifetimeSeconds() <= 0f || descriptor.lifetimeSeconds() > MAX_LIFETIME_SECONDS) {
            return false;
        }
        if (!validateKeyframes(descriptor.sizeOverLife()) || !validateKeyframes(descriptor.rotationOverLife())
                || !validateKeyframes(descriptor.alphaOverLife())) {
            return false;
        }
        if (!validateColors(descriptor.colorOverLife())) {
            return false;
        }
        if (Float.isNaN(descriptor.drag()) || Float.isInfinite(descriptor.drag())) {
            return false;
        }
        if (Float.isNaN(descriptor.gravityMultiplier()) || Float.isInfinite(descriptor.gravityMultiplier())) {
            return false;
        }
        if (descriptor.behaviors().size() > 16) {
            return false;
        }
        if (descriptor.light().block() < 0 || descriptor.light().block() > 15
                || descriptor.light().sky() < 0 || descriptor.light().sky() > 15) {
            return false;
        }
        return descriptor.sortHint() != null;
    }

    private boolean validateKeyframes(List<ScalarKeyframe> frames) {
        if (frames == null) {
            return true;
        }
        if (frames.size() > MAX_KEYFRAMES) {
            return false;
        }
        for (ScalarKeyframe frame : frames) {
            if (frame == null) {
                return false;
            }
            if (frame.t() < 0f || frame.t() > 1f) {
                return false;
            }
            if (Float.isNaN(frame.value()) || Float.isInfinite(frame.value())) {
                return false;
            }
            if (Math.abs(frame.value()) > MAX_SIZE) {
                return false;
            }
        }
        return true;
    }

    private boolean validateColors(List<ColorKeyframe> frames) {
        if (frames == null) {
            return true;
        }
        if (frames.size() > MAX_KEYFRAMES) {
            return false;
        }
        for (ColorKeyframe frame : frames) {
            if (frame == null) {
                return false;
            }
            if (frame.t() < 0f || frame.t() > 1f) {
                return false;
            }
        }
        return true;
    }
}
