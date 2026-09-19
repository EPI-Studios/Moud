package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.ViewModels;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.place.SyncedFiles;
import com.meekdev.moud.mod.transport.payload.PlaceFilePayload;
import com.meekdev.moud.mod.transport.payload.PlaceFileUpPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class PlaceFiles {

    private PlaceFiles() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(PlaceFilePayload.TYPE, (payload, context) ->
                context.client().execute(() -> arrived(payload.res(), payload.exists() ? payload.bytes() : null)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PlaceFiles::dropCopies));
    }

    public static byte @Nullable [] read(String res) {
        Path root = ClientPlace.root();
        if (root == null) return null;
        try {
            return SyncedFiles.read(root, res);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Identifier idOf(String res) {
        String path = Res.parse(res).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return Identifier.fromNamespaceAndPath("moud", "place/" + path);
    }

    public static void upload(Path file) {
        String res = SyncedFiles.res(PlaceToml.root(), file);
        if (res == null) return;
        try {
            upload(res, Files.readAllBytes(file));
        } catch (IOException e) {
            MoudMod.LOG.warn("could not send {} to the server: {}", res, e.getMessage());
        }
    }

    public static void upload(String res, byte[] bytes) {
        if (!SyncedFiles.synced(res) || Minecraft.getInstance().hasSingleplayerServer()) return;
        if (!ClientPlayNetworking.canSend(PlaceFileUpPayload.TYPE)) return;
        if (bytes.length > SyncedFiles.MAX_BYTES) {
            MoudMod.LOG.warn("{} is over {} bytes and stays on this machine", res, SyncedFiles.MAX_BYTES);
            return;
        }
        ClientPlayNetworking.send(PlaceFileUpPayload.write(res, bytes));
    }

    public static void remove(String res, byte[] expected) {
        if (!SyncedFiles.synced(res) || Minecraft.getInstance().hasSingleplayerServer()) return;
        if (!ClientPlayNetworking.canSend(PlaceFileUpPayload.TYPE)) return;
        ClientPlayNetworking.send(PlaceFileUpPayload.remove(res, expected));
    }

    private static void arrived(String res, byte @Nullable [] bytes) {
        if (!SyncedFiles.synced(res)) return;
        if (!Minecraft.getInstance().hasSingleplayerServer()) SyncedFiles.arrived(res, bytes);
        forget(res);
        MoudMod.LOG.info("{} {} on the server", res, bytes == null ? "was removed" : "changed");
    }

    private static void dropCopies() {
        for (String res : SyncedFiles.dropCopies()) forget(res);
    }

    private static void forget(String res) {
        if (SyncedFiles.clip(res)) Animators.forget(res);
        else ViewModels.forget(res);
    }
}
