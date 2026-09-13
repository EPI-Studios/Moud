package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.mod.adapter.store.SqliteStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

// the authoritative tree. the place is built here and the server owns every write to it
public final class ServerScene {

    private static @Nullable InstanceTree tree;
    private static @Nullable Instance world;
    private static @Nullable MinecraftServer server;
    private static final SqliteStore STORE = new SqliteStore(
            () -> server == null ? null : server.getWorldPath(LevelResource.ROOT));

    private ServerScene() {}

    public static @Nullable InstanceTree tree() {
        return tree;
    }

    public static @Nullable Instance world() {
        return world;
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    public static SqliteStore store() {
        return STORE;
    }

    public static boolean running() {
        return world != null;
    }

    static void start(MinecraftServer starting) {
        server = starting;
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
    }

    static void stop() {
        STORE.close();
        server = null;
        tree = null;
        world = null;
    }
}
