package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class ClientPhysics {

    private static final Colliders BOXES = new Colliders();
    private static final ColliderProvider PROVIDER = BOXES::collect;
    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");
    private static final Map<Integer, SubLevelEntity> decks = new HashMap<>();
    private static @Nullable List<SubLevelEntity> indexed;
    private static boolean reindexed;
    private static @Nullable ClientLevel attached;

    private ClientPhysics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static void settle() {
        reindexed = false;
        if (BOXES.settle() && attached != null) {
            LevelPhysics physics = Bkun.physics(attached);
            if (physics != null) physics.invalidateProviders();
        }
    }

    public static void apply(@Nullable InstanceTree tree, Change change) {
        if (tree == null) return;
        if (BOXES.apply(tree, change) && attached != null) {
            LevelPhysics physics = Bkun.physics(attached);
            if (physics != null) physics.invalidateProviders();
        }
        SubLevels.mirror(tree, change);
        drive(tree, change);
    }

    private static void drive(InstanceTree tree, Change change) {
        ClientLevel level = attached;
        if (level == null) return;
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Wrote wrote -> wrote.property() == CFRAME.index() ? wrote.id() : -1;
            case Change.Moved moved -> moved.id();
            case Change.Reset ignored -> -1;
            case Change.Destroyed ignored -> -1;
            case Change.Tagged ignored -> -1;
            case Change.Renamed ignored -> -1;
            case Change.Ordered ignored -> -1;
            case Change.Attributed ignored -> -1;
        };
        if (id < 0 || !(tree.byId(id) instanceof Part part) || !SubLevels.wantsSubLevel(part)) return;

        SubLevelEntity deck = deck(level, id);
        if (deck == null) return;
        CFrame world = Transforms.world(part);
        Quat r = world.rotation();
        deck.drivePose(world.position().x(), world.position().y(), world.position().z(),
                new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
    }

    private static @Nullable SubLevelEntity deck(ClientLevel level, int id) {
        List<SubLevelEntity> live = SubLevelIndex.in(level);
        if (live != indexed) {
            index(live);
            reindexed = false;
        }
        SubLevelEntity deck = decks.get(id);
        if (deck != null && deck.owner() == id) return deck;
        if (reindexed) return null;
        index(live);
        reindexed = true;
        deck = decks.get(id);
        return deck != null && deck.owner() == id ? deck : null;
    }

    private static void index(List<SubLevelEntity> live) {
        indexed = live;
        decks.clear();
        for (SubLevelEntity deck : live) decks.putIfAbsent(deck.owner(), deck);
    }

    public static void attach(@Nullable ClientLevel level) {
        if (level == attached) return;
        if (attached != null) Bkun.collision(attached).removeProvider(PROVIDER);
        attached = level;
        if (level != null) Bkun.collision(level).addProvider(PROVIDER);
    }
}
