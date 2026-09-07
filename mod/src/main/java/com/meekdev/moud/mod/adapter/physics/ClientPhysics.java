package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

// bkun's collide mixin skips its providers for a ServerPlayer, because movement is client
// authoritative and the player's own client has already resolved it. so a part is only solid to
// the player if the client level carries the colliders too, fed from the mirror
public final class ClientPhysics {

    private static final Colliders BOXES = new Colliders();
    private static final ColliderProvider PROVIDER = BOXES::collect;
    private static @Nullable ClientLevel attached;

    private ClientPhysics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static void apply(@Nullable InstanceTree tree, Change change) {
        if (tree != null) BOXES.apply(tree, change);
    }

    // the level a client is in changes without a load event we can hold a provider across, so the
    // attachment is rechecked each frame and moved when it differs
    public static void attach(@Nullable ClientLevel level) {
        if (level == attached) return;
        if (attached != null) Bkun.collision(attached).removeProvider(PROVIDER);
        attached = level;
        if (level != null) Bkun.collision(level).addProvider(PROVIDER);
    }
}
