package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.mod.MoudMod;

final class Ffmpeg {

    private static Boolean available;

    private Ffmpeg() {}

    static boolean available() {
        if (available == null) {
            try {
                Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber", false, Ffmpeg.class.getClassLoader());
                available = true;
            } catch (ClassNotFoundException | LinkageError missing) {
                available = false;
                MoudMod.LOG.warn("this game has no ffmpeg, so its videos cannot play");
            }
        }
        return available;
    }
}
