package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.client.input.Actions;
import com.meekdev.moud.mod.client.input.Input;
import com.meekdev.moud.script.api.SettingsRef;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

public final class PlayerSettings implements SettingsRef {

    public static final PlayerSettings INSTANCE = new PlayerSettings();

    private static final Map<String, Function<Options, KeyMapping>> ACTIONS = new LinkedHashMap<>(Input.GAME_ACTIONS);

    static {
        ACTIONS.put("chat", options -> options.keyChat);
        ACTIONS.put("playerList", options -> options.keyPlayerList);
    }

    private PlayerSettings() {}

    private static Options options() {
        return Minecraft.getInstance().options;
    }

    @Override
    public List<String> volumes() {
        return Arrays.stream(SoundSource.values()).map(SoundSource::getName).toList();
    }

    @Override
    public double volume(String category) {
        return options().getSoundSourceOptionInstance(source(category)).get();
    }

    @Override
    public void volume(String category, double value) {
        options().getSoundSourceOptionInstance(source(category)).set(value);
    }

    private static SoundSource source(String category) {
        for (SoundSource source : SoundSource.values()) {
            if (source.getName().equalsIgnoreCase(category)) return source;
        }
        throw new IllegalArgumentException("'" + category + "' is not a volume");
    }

    @Override
    public double fov() {
        return options().fov().get();
    }

    @Override
    public void fov(double degrees) {
        options().fov().set((int) Math.round(degrees));
    }

    @Override
    public boolean fullscreen() {
        return options().fullscreen().get();
    }

    @Override
    public void fullscreen(boolean on) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            options().fullscreen().set(on);
            if (client.getWindow().isFullscreen() != on) client.getWindow().toggleFullScreen();
        });
    }

    @Override
    public boolean vsync() {
        return options().enableVsync().get();
    }

    @Override
    public void vsync(boolean on) {
        options().enableVsync().set(on);
    }

    @Override
    public int maxFps() {
        return options().framerateLimit().get();
    }

    @Override
    public void maxFps(int fps) {
        options().framerateLimit().set(fps);
    }

    @Override
    public int guiScale() {
        return options().guiScale().get();
    }

    @Override
    public void guiScale(int scale) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            options().guiScale().set(scale);
            client.resizeGui();
        });
    }

    @Override
    public int renderDistance() {
        return options().renderDistance().get();
    }

    @Override
    public void renderDistance(int chunks) {
        options().renderDistance().set(chunks);
    }

    @Override
    public double sensitivity() {
        return options().sensitivity().get();
    }

    @Override
    public void sensitivity(double value) {
        options().sensitivity().set(value);
    }

    @Override
    public List<String> actions() {
        return List.copyOf(ACTIONS.keySet());
    }

    @Override
    public String keyOf(String action) {
        return ACTIONS.get(action).apply(options()).getTranslatedKeyMessage().getString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void bind(String action, String key) {
        int code = Actions.code(key);
        if (!Actions.bindable(code)) throw new IllegalArgumentException("'" + key + "' cannot be bound to a minecraft action, only keys and mouse buttons can");
        InputConstants.Key bound = Actions.mouse(code)
                ? InputConstants.Type.MOUSE.getOrCreate(Actions.button(code))
                : InputConstants.Type.KEYSYM.getOrCreate(code);
        ACTIONS.get(action).apply(options()).setKey(bound);
        KeyMapping.resetMapping();
    }

    @Override
    public void save() {
        options().save();
    }
}
