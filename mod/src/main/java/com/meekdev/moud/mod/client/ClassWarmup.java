package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.mixin.Injections;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClassWarmup {

    private static final Path LIST = Path.of(System.getProperty("user.home"), ".moud", "startup-classes.txt");
    private static final boolean OFF = Boolean.getBoolean("moud.noWarmup");

    private static boolean recorded;

    private ClassWarmup() {}

    public static void start() {
        if (OFF) return;
        Thread.ofPlatform().daemon().name("moud-warmup").start(ClassWarmup::load);
    }

    public static void listen() {
        if (OFF) return;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (recorded) return;
            recorded = true;
            Thread.ofPlatform().daemon().name("moud-warmup-record").start(ClassWarmup::record);
        });
    }

    private static void load() {
        List<String> names;
        try {
            names = Files.readAllLines(LIST);
        } catch (IOException e) {
            return;
        }
        ClassLoader loader = ClassWarmup.class.getClassLoader();
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void record() {
        try {
            Class<?>[] loaded = Injections.loadedClasses();
            ClassLoader loader = ClassWarmup.class.getClassLoader();
            List<String> names = new ArrayList<>(loaded.length);
            for (int i = loaded.length - 1; i >= 0; i--) {
                Class<?> type = loaded[i];
                if (type.getClassLoader() != loader || type.isArray() || type.isHidden()) continue;
                String name = type.getName();
                if (name.startsWith("org.spongepowered.") || name.startsWith("com.llamalad7.")) continue;
                names.add(name);
            }
            Files.createDirectories(LIST.getParent());
            Path temporary = Files.createTempFile(LIST.getParent(), ".startup-classes", ".tmp");
            Files.write(temporary, names);
            Files.move(temporary, LIST, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.debug("could not record the startup classes", e);
        }
    }
}
