package com.moud.client.fakeplayer;

import com.moud.api.math.Quaternion;
import com.moud.client.animation.AnimatedPlayerModel;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.network.MoudPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFakePlayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientFakePlayerManager.class);
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    public ClientFakePlayerManager() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public void handleCreate(MoudPackets.S2C_CreateFakePlayer packet) {
        MinecraftClient.getInstance().execute(() -> {
            MoudPackets.FakePlayerDescriptor desc = packet.descriptor();
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().createModel(desc.id());
            if (model == null) {
                return;
            }
            if (desc.skinUrl() != null && !desc.skinUrl().isBlank()) {
                model.updateSkin(desc.skinUrl());
            }
            if (desc.label() != null && !desc.label().isBlank()) {
                model.getFakePlayer().setCustomName(net.minecraft.text.Text.literal(desc.label()));
                model.getFakePlayer().setCustomNameVisible(true);
            }
            float[] angles = quaternionToYawPitch(desc.rotation());
            model.updatePositionAndRotation(desc.position(), angles[0], angles[1]);
            applyFlags(model.getFakePlayer(), desc.sneaking(), desc.sprinting(), desc.swinging(), desc.usingItem());
            Entry entry = new Entry(model, desc);
            entry.renderPos = desc.position();
            entry.targetPos = desc.position();
            entry.renderYaw = angles[0];
            entry.renderPitch = angles[1];
            entry.targetYaw = angles[0];
            entry.targetPitch = angles[1];
            entry.lastUpdateMs = System.currentTimeMillis();
            entries.put(desc.id(), entry);
            com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance().syncPlayerModel(
                    model.getModelId(),
                    new net.minecraft.util.math.Vec3d(desc.position().x, desc.position().y, desc.position().z),
                    new net.minecraft.util.math.Vec3d(angles[1], angles[0], 0)
            );
        });
    }

    public void handleUpdate(MoudPackets.S2C_UpdateFakePlayer packet) {
        MinecraftClient.getInstance().execute(() -> {
            Entry entry = entries.get(packet.id());
            if (entry == null) {
                return;
            }
            entry.targetPos = packet.position();
            entry.rotation = packet.rotation();
            entry.sneaking = packet.sneaking();
            entry.sprinting = packet.sprinting();
            entry.swinging = packet.swinging();
            entry.usingItem = packet.usingItem();
            float[] angles = quaternionToYawPitch(packet.rotation());
            entry.targetYaw = angles[0];
            entry.targetPitch = angles[1];
            entry.renderPos = packet.position();
            entry.renderYaw = angles[0];
            entry.renderPitch = angles[1];
            entry.lastUpdateMs = System.currentTimeMillis();
            entry.model.updatePositionAndRotation(packet.position(), angles[0], angles[1]);
            com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance().syncPlayerModel(
                    entry.model.getModelId(),
                    new net.minecraft.util.math.Vec3d(packet.position().x, packet.position().y, packet.position().z),
                    new net.minecraft.util.math.Vec3d(angles[1], angles[0], 0)
            );
            applyFlags(entry.model.getFakePlayer(), entry.sneaking, entry.sprinting, entry.swinging, entry.usingItem);
        });
    }

    public void handleRemove(MoudPackets.S2C_RemoveFakePlayer packet) {
        MinecraftClient.getInstance().execute(() -> {
            entries.remove(packet.id());
            ClientPlayerModelManager.getInstance().removeModel(packet.id());
        });
    }

    private void tick() {
        // TODO: Implement interpolation that doesn't struggle interpolation between 2 points
    }

    private void applyFlags(OtherClientPlayerEntity entity, boolean sneaking, boolean sprinting, boolean swinging, boolean usingItem) {
        if (entity == null) return;
        entity.setSneaking(sneaking);
        entity.setSprinting(sprinting);
        if (swinging) {
            entity.swingHand(Hand.MAIN_HAND);
        }
        if (usingItem) {
            entity.setCurrentHand(Hand.MAIN_HAND);
        } else {
            entity.clearActiveItem();
        }
    }

    private float[] quaternionToYawPitch(Quaternion q) {
        if (q == null) {
            return new float[]{0f, 0f};
        }
        double ysqr = q.y * q.y;

        double t0 = +2.0 * (q.w * q.x + q.y * q.z);
        double t1 = +1.0 - 2.0 * (q.x * q.x + ysqr);
        double roll = Math.toDegrees(Math.atan2(t0, t1));

        double t2 = +2.0 * (q.w * q.y - q.z * q.x);
        t2 = Math.max(-1.0, Math.min(1.0, t2));
        double pitch = Math.toDegrees(Math.asin(t2));

        double t3 = +2.0 * (q.w * q.z + q.x * q.y);
        double t4 = +1.0 - 2.0 * (ysqr + q.z * q.z);
        double yaw = Math.toDegrees(Math.atan2(t3, t4));

        // Return yaw (Y) and pitch (X)
        return new float[]{(float) yaw, (float) pitch};
    }

    private static final class Entry {
        private final AnimatedPlayerModel model;
        private MoudPackets.FakePlayerDescriptor descriptor;
        private com.moud.api.math.Vector3 targetPos;
        private com.moud.api.math.Vector3 renderPos = new com.moud.api.math.Vector3(0,0,0);
        private Quaternion rotation;
        private float targetYaw;
        private float targetPitch;
        private float renderYaw;
        private float renderPitch;
        private boolean sneaking;
        private boolean sprinting;
        private boolean swinging;
        private boolean usingItem;
        private long lastUpdateMs;

        private Entry(AnimatedPlayerModel model, MoudPackets.FakePlayerDescriptor descriptor) {
            this.model = model;
            this.descriptor = descriptor;
            this.targetPos = descriptor.position();
            this.rotation = descriptor.rotation();
            this.sneaking = descriptor.sneaking();
            this.sprinting = descriptor.sprinting();
            this.swinging = descriptor.swinging();
            this.usingItem = descriptor.usingItem();
        }
    }
}
