package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.VideoFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.resona.api.PlaySettings;
import com.meekdev.resona.api.Resona;
import com.meekdev.resona.api.SoundHandle;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class Videos {

    private static final PropertyDef PLAYING = Classes.VIDEO_FRAME.property("playing");
    private static final PropertyDef TIME_LENGTH = Classes.VIDEO_FRAME.property("timeLength");
    private static final PropertyDef IS_LOADED = Classes.VIDEO_FRAME.property("isLoaded");
    private static final PropertyDef RESOLUTION = Classes.VIDEO_FRAME.property("resolution");

    private static final class Playback {
        String video = "";
        @Nullable VideoDecoder decoder;
        @Nullable SoundHandle voice;
        VideoDecoder.@Nullable Picture shown;
        int texture;
        int width;
        int height;
        double clock;
        boolean wasPlaying;
        boolean announced;
        boolean over;
        double volume;

        void restart(VideoFrame frame, double from) {
            close();
            clock = from;
            frame.timePosition = from;
            over = false;
            byte[] bytes = PlaceFiles.read(video);
            if (bytes == null) {
                MoudMod.LOG.warn("the video {} is not in the place", video);
                return;
            }
            if (!Ffmpeg.available()) return;
            decoder = new VideoDecoder(video, bytes, from);
        }

        void close() {
            if (voice != null) voice.stop();
            voice = null;
            if (decoder != null) {
                if (shown != null) decoder.recycle(shown);
                decoder.stop();
            }
            decoder = null;
            shown = null;
        }

        void dispose() {
            close();
            Textures.delete(texture);
            texture = 0;
        }
    }

    private static final Identifier VIDEO_SOUND = Identifier.fromNamespaceAndPath("moud", "video");

    private static final Map<VideoFrame, Playback> PLAYBACKS = new IdentityHashMap<>();
    private static long lastFrame;

    private Videos() {}

    public static void frame() {
        long now = System.nanoTime();
        double delta = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1.0e9, 0.25);
        lastFrame = now;

        InstanceTree tree = ClientScene.tree();
        if (tree != null) {
            for (VideoFrame frame : tree.ofClass(Classes.VIDEO_FRAME)) {
                if (Instance.outOfWorld(frame)) continue;
                step(frame, PLAYBACKS.computeIfAbsent(frame, ignored -> new Playback()), delta);
            }
        }
        Iterator<Map.Entry<VideoFrame, Playback>> gone = PLAYBACKS.entrySet().iterator();
        while (gone.hasNext()) {
            Map.Entry<VideoFrame, Playback> entry = gone.next();
            if (tree != null && entry.getKey().isAlive() && entry.getKey().tree() == tree) continue;
            entry.getValue().dispose();
            gone.remove();
        }
    }

    public static int texture(VideoFrame frame) {
        Playback playback = PLAYBACKS.get(frame);
        return playback == null ? 0 : playback.texture;
    }

    private static void step(VideoFrame frame, Playback playback, double delta) {
        if (!frame.video.equals(playback.video)) {
            playback.video = frame.video;
            playback.announced = false;
            if (frame.isLoaded) Instances.setBool(frame, IS_LOADED, false);
            if (frame.video.isEmpty()) playback.close();
            else playback.restart(frame, 0);
        }
        VideoDecoder decoder = playback.decoder;
        if (decoder == null) return;
        announce(frame, playback, decoder);

        if (frame.playing != playback.wasPlaying) {
            playback.wasPlaying = frame.playing;
            if (frame.playing) {
                if (playback.over) playback.restart(frame, 0);
                frame.played.fire(frame);
            } else {
                frame.paused.fire(frame);
            }
        }
        if (Math.abs(frame.timePosition - playback.clock) > 1.0e-6) {
            playback.restart(frame, Math.max(0, frame.timePosition));
            decoder = playback.decoder;
            if (decoder == null) return;
        }
        if (frame.playing) playback.clock += delta;
        show(playback, decoder);
        sound(frame, playback, decoder);

        if (frame.playing && decoder.drained() && ended(playback)) {
            if (frame.looped) {
                playback.restart(frame, 0);
                frame.didLoop.fire(frame);
            } else {
                playback.over = true;
                playback.wasPlaying = false;
                Instances.setBool(frame, PLAYING, false);
                frame.ended.fire(frame);
            }
        }
        frame.timePosition = playback.clock;
    }

    // the sound starts with the first shown picture and follows play and pause from then on
    private static void sound(VideoFrame frame, Playback playback, VideoDecoder decoder) {
        VideoDecoder.Info info = decoder.info();
        if (info == null || info.channels() <= 0) return;
        if (playback.voice == null) {
            if (!frame.playing || playback.shown == null) return;
            PlaySettings settings = PlaySettings.of(VIDEO_SOUND).bus("sfx").volume((float) frame.volume);
            playback.voice = Resona.playStream(new VideoSound(decoder, info.channels(), info.sampleRate()), settings);
            playback.volume = frame.volume;
            return;
        }
        if (frame.volume != playback.volume) {
            playback.voice.setVolume((float) frame.volume);
            playback.volume = frame.volume;
        }
        if (frame.playing == playback.voice.isPaused()) {
            if (frame.playing) playback.voice.resume();
            else playback.voice.pause();
        }
    }

    private static boolean ended(Playback playback) {
        return playback.shown == null || playback.clock >= playback.shown.at() + playback.shown.duration();
    }

    private static void announce(VideoFrame frame, Playback playback, VideoDecoder decoder) {
        if (playback.announced) return;
        VideoDecoder.Info info = decoder.info();
        if (info == null) return;
        playback.announced = true;
        Instances.setNum(frame, TIME_LENGTH, info.length());
        Instances.setObj(frame, RESOLUTION, new Vector3(info.width(), info.height(), 0));
        Instances.setBool(frame, IS_LOADED, true);
        frame.loaded.fire(frame);
    }

    private static void show(Playback playback, VideoDecoder decoder) {
        VideoDecoder.@Nullable Picture due = null;
        VideoDecoder.@Nullable Picture next;
        while ((next = decoder.peek()) != null && (playback.shown == null && due == null || next.at() <= playback.clock)) {
            decoder.poll();
            if (due != null) decoder.recycle(due);
            due = next;
        }
        if (due == null) return;
        if (playback.texture == 0 || playback.width != due.width() || playback.height != due.height()) {
            Textures.delete(playback.texture);
            playback.texture = Textures.frame(due.width(), due.height());
            playback.width = due.width();
            playback.height = due.height();
        }
        Textures.updateFrame(playback.texture, due.width(), due.height(), due.rgba());
        if (playback.shown != null) decoder.recycle(playback.shown);
        playback.shown = due;
    }
}
