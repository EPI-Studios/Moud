package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.transport.payload.EditDownPayload;
import com.meekdev.moud.mod.transport.payload.EditUpPayload;
import com.meekdev.moud.mod.transport.payload.SceneEditPayload;
import com.meekdev.moud.mod.transport.payload.SceneInsertPayload;
import com.meekdev.moud.mod.transport.payload.SceneSavePayload;
import com.meekdev.moud.mod.transport.payload.SceneStatusPayload;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

public final class Editing {

    private static boolean dirty;

    private Editing() {}

    public static void install() {
        ServerPlayNetworking.registerGlobalReceiver(EditUpPayload.TYPE, (payload, context) ->
                context.server().execute(() -> request(context.player(), payload.edit())));
        ServerPlayNetworking.registerGlobalReceiver(SceneEditPayload.TYPE, (payload, context) ->
                context.server().execute(() -> edit(context.player(), payload.changes())));
        ServerPlayNetworking.registerGlobalReceiver(SceneInsertPayload.TYPE, (payload, context) ->
                context.server().execute(() -> insert(context.player(), payload.className(), payload.parent())));
        ServerPlayNetworking.registerGlobalReceiver(SceneSavePayload.TYPE, (payload, context) ->
                context.server().execute(() -> save(context.player())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> joined(handler.getPlayer())));
    }

    static void joined(ServerPlayer player) {
        Place place = MoudServer.place();
        send(player, place != null && place.editing());
        status(player, "", 0);
    }

    static boolean allowed(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server.isSingleplayerOwner(player.nameAndId()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void request(ServerPlayer player, boolean edit) {
        Place place = MoudServer.place();
        if (place == null) return;
        if (!allowed(player)) {
            send(player, place.editing());
            return;
        }
        if (edit == place.editing()) {
            send(player, edit);
            return;
        }
        if (edit) place.edit();
        else place.play();
        dirty = false;
        MoudMod.LOG.info("{} switched the place to {}", player.getGameProfile().name(), edit ? "edit" : "play");
        MinecraftServer server = player.level().getServer();
        MoudServer.respawnAll(server);
        for (ServerPlayer each : server.getPlayerList().getPlayers()) {
            send(each, edit);
            status(each, "", 0);
        }
    }

    private static @Nullable Place editable(ServerPlayer player) {
        Place place = MoudServer.place();
        if (place == null || !place.editing() || !allowed(player) || ServerScene.tree() == null) return null;
        return place;
    }

    private static void edit(ServerPlayer player, byte[] bytes) {
        if (editable(player) == null) return;
        InstanceTree tree = ServerScene.tree();
        Instance world = ServerScene.world();
        List<Change> changes;
        try {
            changes = Codec.decode(bytes, tree, Addons.classes());
        } catch (RuntimeException e) {
            reject(player, "the edit could not be read: " + e.getMessage());
            return;
        }
        Applier applier = new Applier(Addons.classes(), tree, world);
        int applied = 0;
        for (Change change : changes) {
            String refused = refusal(tree, world, change);
            if (refused != null) {
                reject(player, refused);
                continue;
            }
            try {
                applier.apply(change);
                applied++;
            } catch (RuntimeException e) {
                reject(player, e.getMessage());
            }
        }
        if (applied > 0) changed(player.level().getServer());
    }

    private static @Nullable String refusal(InstanceTree tree, Instance world, Change change) {
        return switch (change) {
            case Change.Wrote wrote -> {
                Instance instance = tree.byId(wrote.id());
                if (!sceneOwned(instance, world)) yield "that instance is not part of the scene";
                PropertyDef property = instance.def().property(wrote.property());
                if (property == null) yield "no such property";
                if (property.driven()) yield property.name() + " is driven by the engine";
                if (property.type() == PropertyType.REF && wrote.value() instanceof Integer at && !sceneOwned(tree.byId(at), world)) {
                    yield "a reference has to point into the scene";
                }
                yield null;
            }
            case Change.Moved moved -> {
                Instance instance = tree.byId(moved.id());
                Instance parent = moved.parent() == world.id() ? world : tree.byId(moved.parent());
                if (!sceneOwned(instance, world)) yield "that instance is not part of the scene";
                if (parent != world && !sceneOwned(parent, world)) yield "the new parent is not part of the scene";
                yield null;
            }
            case Change.Destroyed destroyed -> sceneOwned(tree.byId(destroyed.id()), world) ? null : "that instance is not part of the scene";
            case Change.Tagged tagged -> sceneOwned(tree.byId(tagged.id()), world) ? null : "that instance is not part of the scene";
            default -> "the editor can not send that";
        };
    }

    private static boolean sceneOwned(@Nullable Instance instance, Instance world) {
        return instance != null && instance != world && instance.isAlive() && Place.authored(instance);
    }

    private static void insert(ServerPlayer player, String className, int parentId) {
        if (editable(player) == null) return;
        InstanceTree tree = ServerScene.tree();
        Instance world = ServerScene.world();
        ClassDef<?> def = Addons.classes().find(className);
        if (def == null) {
            reject(player, "there is no class " + className);
            return;
        }
        Instance parent = parentId == world.id() ? world : tree.byId(parentId);
        if (parent != world && !sceneOwned(parent, world)) {
            reject(player, "the parent is not part of the scene");
            return;
        }
        Instance made;
        try {
            made = Instances.create(def, parent, className);
        } catch (RuntimeException e) {
            reject(player, e.getMessage());
            return;
        }
        changed(player.level().getServer());
        status(player, "", made.id());
    }

    private static void save(ServerPlayer player) {
        Place place = editable(player);
        if (place == null) return;
        try {
            String file = place.saveScene();
            dirty = false;
            MoudMod.LOG.info("{} saved the scene to {}", player.getGameProfile().name(), file);
            for (ServerPlayer each : player.level().getServer().getPlayerList().getPlayers()) status(each, "saved " + file, 0);
        } catch (RuntimeException e) {
            reject(player, "could not save: " + e.getMessage());
        }
    }

    private static void changed(MinecraftServer server) {
        if (dirty) return;
        dirty = true;
        for (ServerPlayer each : server.getPlayerList().getPlayers()) status(each, "", 0);
    }

    private static void reject(ServerPlayer player, String why) {
        status(player, why, 0);
    }

    private static void status(ServerPlayer player, String message, int inserted) {
        Place place = MoudServer.place();
        if (place == null || !ServerPlayNetworking.canSend(player, SceneStatusPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SceneStatusPayload(dirty, place.sceneFile(), message, inserted));
    }

    private static void send(ServerPlayer player, boolean editing) {
        if (ServerPlayNetworking.canSend(player, EditDownPayload.TYPE)) {
            ServerPlayNetworking.send(player, new EditDownPayload(editing, allowed(player)));
        }
    }
}
