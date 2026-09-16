package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.transport.payload.PushPayload;
import com.meekdev.moud.script.api.PushRef;
import com.meekdev.moud.script.host.HostError;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Push implements PushRef {

    public static final Push INSTANCE = new Push();

    private static final double TICKS = 20.0;

    private Push() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(PushPayload.TYPE, (payload, context) ->
                context.client().execute(() -> apply(new Vector3(payload.x(), payload.y(), payload.z()), payload.replace())));
    }

    @Override
    public void push(Character body, Vector3 perSecond, boolean replace) {
        if (body != ClientScene.own()) throw new HostError("a LocalScript can only push its own body, push %s from a server Script", body.name());
        apply(perSecond, replace);
    }

    private static void apply(Vector3 perSecond, boolean replace) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 change = new Vec3(perSecond.x() / TICKS, perSecond.y() / TICKS, perSecond.z() / TICKS);
        player.setDeltaMovement(replace ? change : player.getDeltaMovement().add(change));
        if (change.y > 0) player.setOnGround(false);
    }
}
