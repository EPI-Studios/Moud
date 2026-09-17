package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.player.Leaderboards;
import com.meekdev.moud.core.player.Leaderstats;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class Scores {

    private Scores() {}

    public static @Nullable Leaderstats of(ServerPlayer player) {
        Instance world = ServerScene.world();
        if (world == null) return null;
        return Leaderboards.of(world, player.getUUID().toString(), player.getGameProfile().name());
    }

    static void joined(ServerPlayer player) {
        of(player);
    }

    static void left(ServerPlayer player) {
        Instance world = ServerScene.world();
        if (world != null) Leaderboards.forget(world, player.getUUID().toString());
    }
}
