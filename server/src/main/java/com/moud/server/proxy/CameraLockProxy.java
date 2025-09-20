package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets; // MODIFIÉ: Import centralisé
import com.moud.server.network.ServerNetworkManager; // MODIFIÉ: Utilisation du nouveau manager
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CameraLockProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraLockProxy.class);
    private final Player player;
    private boolean isLocked = false;
    private Vector3 position = Vector3.zero();
    private Vector3 rotation = Vector3.zero();
    private boolean smoothTransitions = false;
    private float transitionSpeed = 1.0f;
    private boolean disableViewBobbing = true;
    private boolean disableHandMovement = true;

    private static final ScheduledExecutorService animationExecutor = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<Player, ScheduledFuture<?>> activeAnimations = new ConcurrentHashMap<>();

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void shake(float intensity, int durationMs) {
        LOGGER.debug("Player '{}' camera shake called. Intensity: {}, Duration: {}", player.getUsername(), intensity, durationMs);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(intensity, durationMs));
    }


    @HostAccess.Export
    public void lock(Vector3 position, Value options) {
        LOGGER.debug("Player '{}' camera lock called. Position: {}", player.getUsername(), position);
        this.position = position;
        this.isLocked = true;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("yaw")) {
                this.rotation = new Vector3(
                        options.getMember("yaw").asFloat(),
                        options.hasMember("pitch") ? options.getMember("pitch").asFloat() : rotation.y,
                        options.hasMember("roll") ? options.getMember("roll").asFloat() : rotation.z
                );
            }
            if (options.hasMember("smooth")) this.smoothTransitions = options.getMember("smooth").asBoolean();
            if (options.hasMember("speed")) this.transitionSpeed = options.getMember("speed").asFloat();
            if (options.hasMember("disableViewBobbing")) this.disableViewBobbing = options.getMember("disableViewBobbing").asBoolean();
            if (options.hasMember("disableHandMovement")) this.disableHandMovement = options.getMember("disableHandMovement").asBoolean();
        }

        player.setInvisible(true);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.AdvancedCameraLockPacket(
                this.position, this.rotation, this.smoothTransitions, this.transitionSpeed,
                this.disableViewBobbing, this.disableHandMovement, true));
    }

    @HostAccess.Export
    public void setPosition(Vector3 newPosition) {
        LOGGER.debug("Player '{}' camera setPosition called. New Position: {}", player.getUsername(), newPosition);
        this.position = newPosition;
        if (isLocked) {
            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
        }
    }

    @HostAccess.Export
    public void setRotation(Value rotationValue) {
        if (rotationValue != null && rotationValue.hasMembers()) {
            float yaw = rotationValue.hasMember("yaw") ? rotationValue.getMember("yaw").asFloat() : (float) rotation.x;
            float pitch = rotationValue.hasMember("pitch") ? rotationValue.getMember("pitch").asFloat() : (float) rotation.y;
            float roll = rotationValue.hasMember("roll") ? rotationValue.getMember("roll").asFloat() : (float) rotation.z;
            this.rotation = new Vector3(yaw, pitch, roll);
            LOGGER.debug("Player '{}' camera setRotation called. New Rotation: {}", player.getUsername(), this.rotation);

            if (isLocked) {
                ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
            }
        }
    }

    @HostAccess.Export
    public void release() {
        LOGGER.debug("Player '{}' camera release called.", player.getUsername());
        if (!isLocked) {
            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(0, 0));
            return;
        }

        this.isLocked = false;
        stopAnimation();
        player.setInvisible(false);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraReleasePacket(false));
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(0, 0));
    }

    @HostAccess.Export
    public void smoothTransitionTo(Vector3 targetPosition, Value targetRotation, int durationMs) {
        if (!isLocked) return;

        stopAnimation();

        Vector3 startPos = this.position;
        Vector3 startRot = this.rotation;

        Vector3 endRot;
        if (targetRotation != null && targetRotation.hasMembers()) {
            endRot = new Vector3(
                    targetRotation.hasMember("yaw") ? targetRotation.getMember("yaw").asFloat() : startRot.x,
                    targetRotation.hasMember("pitch") ? targetRotation.getMember("pitch").asFloat() : startRot.y,
                    targetRotation.hasMember("roll") ? targetRotation.getMember("roll").asFloat() : startRot.z
            );
        } else {
            endRot = startRot;
        }

        long startTime = System.currentTimeMillis();

        ScheduledFuture<?> transitionTask = animationExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= durationMs) {
                this.position = targetPosition;
                this.rotation = endRot;
                ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
                stopAnimation();
                return;
            }

            float progress = (float) elapsed / durationMs;
            progress = easeInOutCubic(progress);

            this.position = lerpVector3(startPos, targetPosition, progress);
            this.rotation = lerpVector3(startRot, endRot, progress);

            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));

        }, 0, 16, TimeUnit.MILLISECONDS);

        activeAnimations.put(player, transitionTask);
    }

    @HostAccess.Export
    public void stopAnimation() {
        ScheduledFuture<?> currentAnimation = activeAnimations.remove(player);
        if (currentAnimation != null) {
            currentAnimation.cancel(false);
        }
    }

    @HostAccess.Export
    public boolean isLocked() {
        return isLocked;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public Vector3 getRotation() {
        return rotation;
    }

    private Vector3 lerpVector3(Vector3 start, Vector3 end, float t) {
        return new Vector3(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t, start.z + (end.z - start.z) * t);
    }

    private float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}