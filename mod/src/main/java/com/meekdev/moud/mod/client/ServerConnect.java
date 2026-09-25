package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.screens.PlaceMessageScreen;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.place.ServerLookup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class ServerConnect {

    private static final String CACHE = ".moud/server.json";

    private static ServerLookup.@Nullable Result lookup;
    private static int next;
    private static boolean started;
    private static volatile boolean joined;
    private static boolean waiting;
    private static String failure = "";
    private static String download = "";
    private static final List<String> NOTES = new ArrayList<>();

    private ServerConnect() {}

    public static void install() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!active()) return;
            joined = true;
            NOTES.clear();
            MoudMod.LOG.info("joined {}", handler.getConnection().getRemoteAddress());
        });
    }

    public static boolean active() {
        if (!PlaceToml.chosenAtLaunch()) return false;
        if (!Game.standalone() && !Boolean.getBoolean("moud.connect")) return false;
        return PlaceToml.config().host().set();
    }

    public static boolean connecting() {
        return waiting && !joined && active();
    }

    public static String status() {
        return "Connecting to " + PlaceToml.config().name();
    }

    public static List<String> notes() {
        return NOTES;
    }

    public static String download() {
        return download;
    }

    public static void lost(Component reason) {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new GenericMessageScreen(Component.literal(status())));
        client.schedule(() -> disconnected(client, reason));
    }

    public static void home() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new GenericMessageScreen(Component.literal(status())));
        if (started) {
            client.schedule(() -> disconnected(client, Component.literal("The connection closed")));
            return;
        }
        started = true;
        if (Game.standalone() && Files.isRegularFile(PlaceToml.root().resolve("icon.png"))) WindowApi.INSTANCE.icon("res://icon.png");
        client.schedule(() -> start(client));
    }

    public static void start(Minecraft client) {
        started = true;
        waiting = true;
        joined = false;
        lookup = null;
        next = 0;
        failure = "No server answered";
        download = "";
        NOTES.clear();
        LoadingState.begin(LoadingState.LOOKUP);
        LoadingPlace.start();
        client.setScreen(new GenericMessageScreen(Component.literal(status())));
        PlaceConfig.Host host = PlaceToml.config().host();
        Path cache = PlaceToml.root().resolve(CACHE);
        Thread worker = new Thread(() -> {
            try {
                ServerLookup.Found found = ServerLookup.find(host, cache);
                client.execute(() -> looked(client, found));
            } catch (IOException e) {
                MoudMod.LOG.warn("the server lookup failed: {}", e.toString());
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                client.execute(() -> failed(client, "The server list could not be loaded: " + ConnectReasons.friendly(why)));
            }
        }, "moud server lookup");
        worker.setDaemon(true);
        worker.start();
    }

    private static void looked(Minecraft client, ServerLookup.Found found) {
        ServerLookup.Result result = found.result();
        String name = PlaceToml.config().name();
        String version = PlaceToml.config().version();
        if (found.problem() != null) {
            MoudMod.LOG.warn("the server lookup failed, using the saved one: {}", found.problem());
            NOTES.add("Using the servers from last time, " + found.problem());
        }
        download = result.download();
        if (!result.open()) {
            MoudMod.LOG.info("{} is closed: {}", name, result.message());
            show(client, name, result.message().isEmpty() ? "The server is closed for now." : result.message(), LoadingState.CLOSED);
            return;
        }
        if (result.outdated(version)) {
            MoudMod.LOG.info("{} {} is older than the {} the server needs", name, version, result.version());
            String reason = name + " " + result.version() + " is out, this is " + version + ". Download the new version to keep playing.";
            if (result.download().isEmpty()) {
                show(client, "This game needs an update", reason, LoadingState.UPDATE);
            } else {
                String link = result.download();
                fail(client, "This game needs an update", reason, LoadingState.UPDATE, "Download", () -> {
                    MoudMod.LOG.info("opening {}", link);
                    Util.getPlatform().openUri(link);
                });
            }
            return;
        }
        if (!result.message().isEmpty()) NOTES.addFirst(result.message());
        if (!NOTES.isEmpty()) LoadingState.message(NOTES.getFirst());
        lookup = result;
        attempt(client);
    }

    private static void attempt(Minecraft client) {
        ServerLookup.Result result = lookup;
        if (result == null || next >= result.servers().size()) {
            failed(client, failure);
            return;
        }
        String address = result.servers().get(next++);
        if (!ServerAddress.isValidAddress(address)) {
            failure = address + " is not a server address";
            attempt(client);
            return;
        }
        MoudMod.LOG.info("connecting to {} ({} of {})", address, next, result.servers().size());
        LoadingState.server(address);
        LoadingState.phase(LoadingState.CONNECTING);
        ServerData data = new ServerData(PlaceToml.config().name(), address, ServerData.Type.OTHER);
        Screen screen = new GenericMessageScreen(Component.literal(status()));
        client.setScreen(screen);
        ConnectScreen.startConnecting(screen, client, ServerAddress.parseString(address), data, false, null);
    }

    private static void disconnected(Minecraft client, Component reason) {
        if (joined) {
            joined = false;
            MoudMod.LOG.info("disconnected: {}", reason.getString());
            show(client, "Disconnected", ConnectReasons.friendly(reason.getString()), LoadingState.DISCONNECTED);
            return;
        }
        failure = ConnectReasons.friendly(reason.getString());
        MoudMod.LOG.info("could not connect: {}", reason.getString());
        attempt(client);
    }

    private static void failed(Minecraft client, String reason) {
        show(client, "Could not connect to " + PlaceToml.config().name(), reason, LoadingState.CONNECT);
    }

    private static void show(Minecraft client, String title, String reason, String kind) {
        fail(client, title, reason, kind, "Try again", () -> start(client));
    }

    private static void fail(Minecraft client, String title, String reason, String kind, String action, Runnable run) {
        waiting = false;
        if (LoadingState.fail(reason, kind)) {
            client.setScreen(new GenericMessageScreen(Component.literal(title)));
            return;
        }
        client.setScreen(new PlaceMessageScreen(Component.literal(title), Component.literal(reason), action, run));
    }
}
