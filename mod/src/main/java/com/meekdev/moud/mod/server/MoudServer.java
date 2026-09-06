package com.meekdev.moud.mod.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

public final class MoudServer {

    private MoudServer() {}

    public static void install() {
        ServerPlayerEvents.JOIN.register(MoudServer::place);
    }

    // scaffolding until the character lands: nothing holds a player up in a void level
    private static void place(ServerPlayer player) {
        player.teleportTo(0.5, 70.0, 0.5);
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
    }
}
