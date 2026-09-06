package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.moud.mod.MoudMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

// the seam to bkun: one provider, moved to whichever level the client is in
public final class Physics {

    private Physics() {}

    public static void install(Colliders colliders) {
        Holder holder = new Holder();
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            if (holder.level != null) Bkun.collision(holder.level).removeProvider(colliders);
            holder.level = level;
            if (level == null) return;
            Bkun.collision(level).addProvider(colliders);
            MoudMod.LOG.info("parts collide, {} in the grid", colliders.size());
        });
    }

    private static final class Holder {
        @Nullable Level level;
    }
}
