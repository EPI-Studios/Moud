package com.meekdev.moud.mod.client.editor.animation;

public record KeyRef(String joint, Channel channel, double time) {

    public KeyRef {
        time = clean(time);
    }

    public static double clean(double time) {
        double rounded = Math.round(time * 1e6) / 1e6;
        return rounded == 0 ? 0 : rounded;
    }
}
