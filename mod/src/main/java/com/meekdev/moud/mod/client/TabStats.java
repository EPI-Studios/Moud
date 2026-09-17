package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.player.Leaderboards;
import com.meekdev.moud.core.player.Leaderstats;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class TabStats {

    private TabStats() {}

    public static String of(UUID player) {
        Leaderstats stats = find(player);
        return stats == null ? "" : Leaderboards.columns(stats);
    }

    public static double leading(UUID player) {
        Leaderstats stats = find(player);
        return stats == null ? Double.NEGATIVE_INFINITY : Leaderboards.leading(stats);
    }

    public static boolean any() {
        Instance world = ClientScene.world();
        return world != null && world.child(Leaderboards.NAME) != null;
    }

    private static @Nullable Leaderstats find(UUID player) {
        Instance world = ClientScene.world();
        return world == null ? null : Leaderboards.find(world, player.toString());
    }
}
