package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.api.LoadingRef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;

public final class LoadingState {

    public static final String LOOKUP = "lookup";
    public static final String CONNECTING = "connecting";
    public static final String JOINING = "joining";
    public static final String WORLD = "world";
    public static final String READY = "ready";

    public static final String CONNECT = "connect";
    public static final String UPDATE = "update";
    public static final String CLOSED = "closed";
    public static final String DISCONNECTED = "disconnected";

    private record Watcher(LoadingRef.Listener listener, boolean lasting) {}

    private static final List<Watcher> WATCHERS = new ArrayList<>();

    private static String phase = LOOKUP;
    private static double progress;
    private static String message = "";
    private static String server = "";
    private static boolean hold;
    private static boolean failing;

    private LoadingState() {}

    public static String phase() {
        return phase;
    }

    public static double progress() {
        return progress;
    }

    public static String message() {
        return message;
    }

    public static String server() {
        return server;
    }

    public static boolean hold() {
        return hold;
    }

    static void hold(boolean on) {
        hold = on;
    }

    static boolean failing() {
        return failing;
    }

    public static void begin(String first) {
        phase = first;
        progress = floor(first);
        message = "";
        server = "";
        failing = false;
        MoudMod.LOG.info("loading: {}", first);
        changed();
    }

    public static void phase(String next) {
        if (phase.equals(next) && !failing) return;
        phase = next;
        failing = false;
        progress = Math.max(progress, floor(next));
        MoudMod.LOG.info("loading: {}", next);
        changed();
        if (!next.equals(READY)) return;
        for (Watcher watcher : List.copyOf(WATCHERS)) watcher.listener().ready();
        if (!hold) LoadingPlace.finish();
    }

    public static void server(String address) {
        if (server.equals(address)) return;
        server = address;
        changed();
    }

    public static void message(String text) {
        if (message.equals(text)) return;
        message = text;
        changed();
    }

    public static boolean fail(String reason, String kind) {
        LoadingPlace.start();
        failing = true;
        message = reason;
        changed();
        boolean handled = false;
        for (Watcher watcher : List.copyOf(WATCHERS)) {
            if (watcher.listener().failed(reason, kind) && watcher.lasting()) handled = true;
        }
        return handled;
    }

    static void listen(LoadingRef.Listener listener, boolean lasting) {
        WATCHERS.add(new Watcher(listener, lasting));
    }

    static void forget(LoadingRef.Listener listener) {
        WATCHERS.removeIf(watcher -> watcher.listener() == listener);
    }

    static void tick(Minecraft client) {
        if (phase.equals(READY) || failing) return;
        if (client.screen instanceof LevelLoadingScreen loading) {
            LevelLoadTracker tracker = loading.loadTracker;
            if (tracker != null && tracker.hasProgress()) {
                double at = floor(phase) + (ceiling(phase) - floor(phase)) * Math.clamp(tracker.serverProgress(), 0, 1);
                if (at > progress + 0.001) {
                    progress = at;
                    changed();
                }
            }
            return;
        }
        if (phase.equals(WORLD) && client.level != null && ClientPlace.running()) phase(READY);
    }

    private static void changed() {
        for (Watcher watcher : List.copyOf(WATCHERS)) watcher.listener().changed();
    }

    private static double floor(String phase) {
        return switch (phase) {
            case CONNECTING -> 0.1;
            case JOINING -> 0.4;
            case WORLD -> 0.5;
            case READY -> 1;
            default -> 0;
        };
    }

    private static double ceiling(String phase) {
        return switch (phase) {
            case LOOKUP -> 0.1;
            case CONNECTING -> 0.4;
            case JOINING -> 0.5;
            case WORLD -> 0.95;
            default -> 1;
        };
    }
}
