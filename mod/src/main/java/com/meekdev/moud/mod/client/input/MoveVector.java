package com.meekdev.moud.mod.client.input;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

final class MoveVector {

    private MoveVector() {}

    static void set(ClientInput input, Vec2 vector) {
        input.moveVector = vector;
    }
}
