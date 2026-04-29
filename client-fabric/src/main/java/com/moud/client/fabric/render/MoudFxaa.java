package com.moud.client.fabric.render;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MoudFxaa {

    public static final String EFFECT_ID = "moud_fxaa";

    private static String fragSource;
    private static volatile boolean enabled;

    private MoudFxaa() {}

    public static void init() {
        fragSource = loadResource("assets/moud/shaders/builtin/fxaa.frag");
        setEnabled(true);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) {
            if (fragSource == null || fragSource.isBlank()) {
                return;
            }
            PostProcessService.INSTANCE.registerInline(EFFECT_ID, fragSource, 1000, PostProcessStage.WORLD);
        } else {
            PostProcessService.INSTANCE.unregister(EFFECT_ID);
        }
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    private static String loadResource(String path) {
        try (InputStream is = MoudFxaa.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
