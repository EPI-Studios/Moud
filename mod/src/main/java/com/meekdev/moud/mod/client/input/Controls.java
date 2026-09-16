package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.mod.transport.payload.ControlsPayload;
import com.meekdev.moud.script.api.ControlsRef;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class Controls {

    private static volatile ControlsPayload server = new ControlsPayload(true, true, true);
    private static final Map<String, Boolean> LOCAL = new HashMap<>();

    private Controls() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(ControlsPayload.TYPE, (payload, context) -> server = payload);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            server = new ControlsPayload(true, true, true);
            LOCAL.clear();
        });
    }

    public static ControlsRef local() {
        LOCAL.clear();
        return new ControlsRef() {
            @Override
            public boolean enabled(String control) {
                check(control);
                return LOCAL.getOrDefault(control, true);
            }

            @Override
            public void enabled(String control, boolean on) {
                check(control);
                LOCAL.put(control, on);
            }
        };
    }

    public static boolean move() {
        return server.move() && LOCAL.getOrDefault("move", true);
    }

    public static boolean jump() {
        return server.jump() && LOCAL.getOrDefault("jump", true);
    }

    public static boolean look() {
        return server.look() && LOCAL.getOrDefault("look", true);
    }

    public static void apply(ClientInput input) {
        boolean move = move();
        boolean jump = jump();
        if (move && jump) return;
        Input pressed = input.keyPresses;
        input.keyPresses = new Input(move && pressed.forward(), move && pressed.backward(), move && pressed.left(), move && pressed.right(),
                jump && pressed.jump(), pressed.shift(), move && pressed.sprint());
        if (!move) {
            MoveVector.set(input, Vec2.ZERO);
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.setSprinting(false);
        }
    }

    private static void check(String control) {
        if (!ControlsRef.NAMES.contains(control)) throw new IllegalArgumentException("'" + control + "' is not a control, expected move, jump or look");
    }
}
