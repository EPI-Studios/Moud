package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.collision.ColliderSink;
import com.meekdev.moud.mod.client.ClientScene;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

// the seam to bkun. the provider is registered once and looks the current colliders up per query,
// because the mirror replaces its tree on every reload and the registration must outlive that
public final class Physics {

    private Physics() {}

    public static void install() {
        Holder holder = new Holder();
        ColliderProvider provider = (region, sink) -> collect(region, sink);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            if (holder.level != null) Bkun.collision(holder.level).removeProvider(provider);
            holder.level = level;
            if (level != null) Bkun.collision(level).addProvider(provider);
        });
    }

    private static void collect(AABB region, ColliderSink sink) {
        Colliders colliders = ClientScene.colliders();
        if (colliders != null) colliders.collect(region, sink);
    }

    private static final class Holder {
        @Nullable Level level;
    }
}
