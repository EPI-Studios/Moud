package com.meekdev.moud.mod.client.editor.style;

import org.lwjgl.glfw.GLFW;

public final class EditorScale {

    public static final float AUTOMATIC = 0.0f;
    public static final float MINIMUM_FACTOR = 0.75f;
    public static final float MAXIMUM_FACTOR = 3.0f;

    private static float factor = 1.0f;

    private EditorScale() {
    }

    public static float factor() {
        return factor;
    }

    public static void setFactor(float value) {
        factor = Math.clamp(value, MINIMUM_FACTOR, MAXIMUM_FACTOR);
    }

    public static void applyPreference(float preferred) {
        setFactor(preferred <= AUTOMATIC ? detectDisplayScale() : preferred);
    }

    public static float detectDisplayScale() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return 1.0f;
        }
        float[] horizontal = new float[1];
        float[] vertical = new float[1];
        GLFW.glfwGetMonitorContentScale(monitor, horizontal, vertical);
        float detected = Math.max(horizontal[0], vertical[0]);
        return detected <= 0.0f ? 1.0f : detected;
    }

    public static float of(float designPixels) {
        return Math.round(designPixels * factor);
    }

    public static float ofExact(float designPixels) {
        return designPixels * factor;
    }

    public static int ofInteger(float designPixels) {
        return Math.round(designPixels * factor);
    }

    public static float ofAtLeastOne(float designPixels) {
        return Math.max(1.0f, Math.round(designPixels * factor));
    }
}
