package com.meekdev.moud.mod.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

// the move vector is protected on the game's input, so it is written through the accessor the widener opens
final class MoveVector {

    private MoveVector() {}

    static void set(ClientInput input, Vec2 vector) {
        input.moveVector = vector;
    }
}
