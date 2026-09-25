package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.BillboardGui;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.ui.VideoFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.resona.api.PlaySettings;
import com.meekdev.resona.api.Resona;
import com.meekdev.resona.api.SoundHandle;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
        boolean spatial;
        int wrap = Textures.CLAMP;
        boolean muted;
        long seen;

        void restart(VideoFrame frame, double from) {
            start(from);
            frame.timePosition = from;
        }

        void start(double from) {
            close();
            clock = from;
            over = false;
            byte[] bytes = PlaceFiles.read(video);
            if (bytes == null) {
                MoudMod.LOG.warn("the video {} is not in the place", video);
                return;
            }
            if (!Ffmpeg.available()) return;
            decoder = new VideoDecoder(video, bytes, from, !muted);
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

    private static final List<String> FILES = List.of(".mp4", ".webm", ".ogv", ".mov", ".mkv", ".avi", ".m4v");
    private static final long UNSEEN_NANOS = 2_000_000_000L;
    private static final float NEAR = 8;
    private static final float FAR = 48;

    private static final Map<VideoFrame, Playback> PLAYBACKS = new IdentityHashMap<>();
    private static final Map<String, Playback> LOOPS = new HashMap<>();
    private static long lastFrame;

    private Videos() {}

    public static boolean isVideo(String src) {
        if (!src.startsWith(Res.SCHEME)) return false;
        String name = src.toLowerCase(Locale.ROOT);
        for (String file : FILES) {
            if (name.endsWith(file)) return true;
        }
        return false;
    }

    public static int texture(String video) {
        Playback loop = LOOPS.get(video);
        if (loop == null) {
            loop = new Playback();
            loop.video = video;
            loop.wrap = Textures.REPEAT;
            loop.muted = true;
            loop.start(0);
            LOOPS.put(video, loop);
        }
        loop.seen = System.nanoTime();
        return loop.texture;
    }

    public static int @Nullable [] size(String video) {
        Playback loop = LOOPS.get(video);
        return loop == null || loop.texture == 0 ? null : new int[] {loop.width, loop.height};
    }

    public static void frame(List<InstanceTree> trees, float partialTick) {
        long now = System.nanoTime();
        double delta = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1.0e9, 0.25);
        lastFrame = now;

        for (InstanceTree tree : trees) {
            for (VideoFrame frame : tree.ofClass(Classes.VIDEO_FRAME)) {
                if (Instance.outOfWorld(frame)) continue;
                step(frame, PLAYBACKS.computeIfAbsent(frame, ignored -> new Playback()), delta, partialTick);
            }
        }
        Iterator<Map.Entry<VideoFrame, Playback>> gone = PLAYBACKS.entrySet().iterator();
        while (gone.hasNext()) {
            Map.Entry<VideoFrame, Playback> entry = gone.next();
            if (entry.getKey().isAlive() && trees.contains(entry.getKey().tree())) continue;
            entry.getValue().dispose();
            gone.remove();
        }
        Iterator<Playback> loops = LOOPS.values().iterator();
        while (loops.hasNext()) {
            Playback loop = loops.next();
            if (!trees.isEmpty() && now - loop.seen < UNSEEN_NANOS) {
                loop(loop, delta);
                continue;
            }
            loop.dispose();
            loops.remove();
        }
    }

    private static void loop(Playback loop, double delta) {
        VideoDecoder decoder = loop.decoder;
        if (decoder == null) return;
        loop.clock += delta;
        show(loop, decoder);
        if (!decoder.drained() || !ended(loop)) return;
        if (loop.shown == null) loop.close();
        else loop.start(0);
    }

    public static int texture(VideoFrame frame) {
        Playback playback = PLAYBACKS.get(frame);
        return playback == null ? 0 : playback.texture;
    }

    private static void step(VideoFrame frame, Playback playback, double delta, float partialTick) {
        if (!frame.video.equals(playback.video)) {
            playback.video = frame.video;
            playback.announced = false;
            if (frame.isLoaded) Instances.setBool(frame, IS_LOADED, false);
            if (frame.video.isEmpty()) playback.dispose();
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
        sound(frame, playback, decoder, partialTick);

        if (frame.playing && decoder.drained() && ended(playback)) {
            if (frame.looped && playback.shown != null) {
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

    private static void sound(VideoFrame frame, Playback playback, VideoDecoder decoder, float partialTick) {
        VideoDecoder.Info info = decoder.info();
        if (info == null || info.channels() <= 0) return;
        Vector3 at = placed(frame, partialTick);
        if (playback.voice != null && playback.spatial != (at != null)) {
            playback.voice.stop();
            playback.voice = null;
        }
        if (playback.voice == null) {
            if (!frame.playing || playback.shown == null) return;
            PlaySettings settings = PlaySettings.of(VIDEO_SOUND).bus("sfx").volume((float) frame.volume);
            if (at != null) settings.at(new Vec3(at.x(), at.y(), at.z())).distance(NEAR, FAR, 1);
            playback.voice = Resona.playStream(new VideoSound(decoder, info.channels(), info.sampleRate()), settings);
            playback.volume = frame.volume;
            playback.spatial = at != null;
            return;
        }
        if (at != null) playback.voice.setPosition(new Vec3(at.x(), at.y(), at.z()));
        if (frame.volume != playback.volume) {
            playback.voice.setVolume((float) frame.volume);
            playback.volume = frame.volume;
        }
        if (frame.playing == playback.voice.isPaused()) {
            if (frame.playing) playback.voice.resume();
            else playback.voice.pause();
        }
    }

    private static @Nullable Vector3 placed(VideoFrame frame, float partialTick) {
        for (Instance at = frame.parent(); at != null; at = at.parent()) {
            if (!(at instanceof SurfaceGui) && !(at instanceof BillboardGui)) continue;
            Instance target = GuiLayout.adornee(at);
            if (!(target instanceof Spatial) || target.parent() == null) return null;
            return ClientScene.motion().sample(target, partialTick).position();
        }
        return null;
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
            playback.texture = Textures.frame(due.width(), due.height(), playback.wrap);
            playback.width = due.width();
            playback.height = due.height();
        }
        Textures.updateFrame(playback.texture, due.width(), due.height(), due.rgba());
        if (playback.shown != null) decoder.recycle(playback.shown);
        playback.shown = due;
    }
}
