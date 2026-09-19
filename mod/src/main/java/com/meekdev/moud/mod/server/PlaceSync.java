package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.SyncedFiles;
import com.meekdev.moud.mod.transport.payload.PlaceFilePayload;
import com.meekdev.moud.mod.transport.payload.PlaceFileUpPayload;
import com.meekdev.moud.script.reload.Watcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class PlaceSync {

    private static final byte[] NONE = new byte[0];
    private static final SyncedFiles SENT = new SyncedFiles();

    private static @Nullable Path root;
    private static @Nullable Watcher watcher;

    private PlaceSync() {}

    public static void install() {
        ServerPlayNetworking.registerGlobalReceiver(PlaceFileUpPayload.TYPE, (payload, context) ->
                context.server().execute(() -> upload(context.player(), payload.res(), payload.bytes())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> joined(handler.getPlayer())));
    }

    static void start(Path place) {
        stop();
        root = place.toAbsolutePath().normalize();
        try {
            watcher = new Watcher(root, SyncedFiles.EXTENSIONS);
        } catch (IOException e) {
            MoudMod.LOG.warn("could not watch the clips and models of {}, players will not see them change", root, e);
        }
    }

    static void stop() {
        if (watcher != null) watcher.close();
        watcher = null;
        root = null;
        SENT.clear();
    }

    static void tick(MinecraftServer server) {
        if (watcher == null || root == null) return;
        for (Path path : watcher.changes()) {
            String res = SyncedFiles.res(root, path);
            if (res == null) continue;
            byte[] bytes;
            try {
                bytes = Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
            } catch (IOException e) {
                continue;
            }
            if (bytes != null && bytes.length > SyncedFiles.MAX_BYTES) {
                MoudMod.LOG.warn("{} is over {} bytes, players keep the copy they have", res, SyncedFiles.MAX_BYTES);
                continue;
            }
            changed(server, res, bytes);
        }
    }

    private static void changed(MinecraftServer server, String res, byte @Nullable [] bytes) {
        if (!SENT.note(res, bytes)) return;
        if (SyncedFiles.clip(res)) Animators.forget(res);
        PlaceFilePayload payload = payload(res, bytes);
        int told = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (send(player, payload)) told++;
        }
        MoudMod.LOG.info("{} {}, sent it to {} players", res, bytes == null ? "was removed" : "changed", told);
    }

    private static void joined(ServerPlayer player) {
        for (Map.Entry<String, byte @Nullable []> one : SENT.changed().entrySet()) send(player, payload(one.getKey(), one.getValue()));
    }

    private static PlaceFilePayload payload(String res, byte @Nullable [] bytes) {
        return new PlaceFilePayload(res, bytes != null, bytes == null ? NONE : bytes);
    }

    private static boolean send(ServerPlayer player, PlaceFilePayload payload) {
        if (!ServerPlayNetworking.canSend(player, PlaceFilePayload.TYPE)) return false;
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    private static void upload(ServerPlayer player, String res, byte[] bytes) {
        String name = player.getGameProfile().name();
        if (!Editing.allowed(player)) {
            MoudMod.LOG.warn("{} tried to write {} without edit rights", name, res);
            Editing.status(player, "only operators can save " + res + " on this server");
            return;
        }
        if (root == null) return;
        try {
            SyncedFiles.write(root, res, bytes);
        } catch (IOException | RuntimeException e) {
            Editing.status(player, "could not save " + res + " on the server: " + e.getMessage());
            return;
        }
        MoudMod.LOG.info("{} saved {} on the server", name, res);
        Output.add(Output.Level.SYSTEM, "server", name + " saved " + res);
        changed(player.level().getServer(), res, bytes);
        Editing.status(player, "saved " + res + " on the server");
    }
}
