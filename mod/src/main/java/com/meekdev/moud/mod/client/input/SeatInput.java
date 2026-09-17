package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.transport.payload.SeatPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

public final class SeatInput {

    private static int throttle;
    private static int steer;

    private SeatInput() {}

    public static void apply(ClientInput input) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ClientPlayNetworking.canSend(SeatPayload.TYPE)) return;
        boolean seated = seated(player);
        Input pressed = input.keyPresses;
        int wantedThrottle = !seated ? 0 : (pressed.forward() ? 1 : 0) + (pressed.backward() ? -1 : 0);
        int wantedSteer = !seated ? 0 : (pressed.right() ? 1 : 0) + (pressed.left() ? -1 : 0);
        if (wantedThrottle == throttle && wantedSteer == steer) return;
        throttle = wantedThrottle;
        steer = wantedSteer;
        ClientPlayNetworking.send(new SeatPayload(throttle, steer));
    }

    private static boolean seated(LocalPlayer player) {
        Character body = Bodies.of(ClientScene.tree(), player.getUUID().toString());
        return body != null && Rig.humanoid(body) instanceof Humanoid living && living.sit;
    }

    public static void clear() {
        throttle = 0;
        steer = 0;
    }
}
