package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.client.editor.project.ProjectHubScreen;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.server.VoidLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.jspecify.annotations.Nullable;

public final class Launch {

    private static Features features;
    private boolean started;

    public Launch(Features features) {
        Launch.features = features;
    }

    public void install() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (PlaceToml.chosenAtLaunch() && !features.isOn(Feature.TITLE_SCREEN) && !ServerConnect.active() && !joining()) LevelPreload.start(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LevelPreload.cancel());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LevelPreload.cancel());
        ServerConnect.install();
        LoadingPlace.install();
    }

    public static @Nullable Path projectToOpen() {
        String project = System.getProperty("moud.openProject");
        return project == null || project.isBlank() ? null : Path.of(project);
    }

    public static boolean joining() {
        return List.of(FabricLoader.getInstance().getLaunchArguments(true)).contains("--quickPlayMultiplayer");
    }

    private void tick(Minecraft client) {
        if (PlaceToml.chosenAtLaunch() && client.screen instanceof AccessibilityOnboardingScreen onboarding) {
            client.options.onboardingAccessibilityFinished();
            onboarding.onClose.run();
            return;
        }
        if (!PlaceToml.chosenAtLaunch() && client.screen instanceof ProjectHubScreen && client.level == null && client.getSingleplayerServer() == null) {
            LevelPreload.start(client);
            Path project = projectToOpen();
            if (project != null && !started) {
                started = true;
                MoudMod.LOG.info("opening {} from the hub", project);
                openProject(project);
            }
            return;
        }
        if (!(client.screen instanceof TitleScreen)) return;
        if (!PlaceToml.chosenAtLaunch() && Editor.available()) {
            Editor.openHub(client);
            return;
        }
        if (started || features.isOn(Feature.TITLE_SCREEN) || joining()) return;
        started = true;
        if (Game.standalone() && Files.isRegularFile(PlaceToml.root().resolve("icon.png"))) WindowApi.INSTANCE.icon("res://icon.png");
        open(client);
    }

    public static void openProject(Path root) {
        PlaceToml.open(root, true, features);
        open(Minecraft.getInstance());
    }

    public static void reopen() {
        open(Minecraft.getInstance());
    }

    private static boolean opened;

    public static boolean opened() {
        return opened;
    }

    private static void open(Minecraft client) {
        opened = true;
        client.getTutorial().setStep(TutorialSteps.NONE);
        LoadingState.begin(LoadingState.CONNECTING);
        LoadingPlace.start();
        if (LevelPreload.open(client)) return;
        client.createWorldOpenFlows().createFreshLevel(
                VoidLevel.NAME,
                VoidLevel.settings(),
                WorldOptions.defaultWithRandomSeed(),
                VoidLevel::dimensions,
                new TitleScreen());
    }
}
