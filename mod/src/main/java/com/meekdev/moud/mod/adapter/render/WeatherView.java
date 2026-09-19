package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.CeilingGrid;
import com.meekdev.moud.core.effect.Tally;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.render.Environments;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.core.render.Lightning;
import com.meekdev.moud.core.render.Weather;
import com.meekdev.moud.core.render.WeatherKind;
import com.meekdev.moud.core.render.WeatherLevels;
import com.meekdev.moud.core.render.WeatherMix;
import com.meekdev.moud.core.render.Weathers;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.script.api.AudioRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public final class WeatherView {

    private record Thunder(double at, Vector3 where, double distance) {}

    private record Flash(double at, Vector3 where) {}

    public static final int ROOF_REACH = 24;

    private static final double LONGEST_FRAME = 0.1;
    private static final double SETTLE = 3;
    private static final double ROOF_EVERY = 0.25;
    private static final double ROOF_ABOVE = 48;
    private static final double ROOF_BELOW = 24;
    private static final double QUIET = 0.005;
    private static final double FADE = 1.5;
    private static final double CLOSE_STRIKE = 48;
    private static final double LOCAL_NEAREST = 24;
    private static final double LOCAL_FARTHEST = 80;
    private static final double FLASH_SPACING = 0.12;

    private static final WeatherMix MIX = new WeatherMix();
    private static final CeilingGrid ROOFS = new CeilingGrid(ROOF_REACH);
    private static final List<Thunder> THUNDER = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final Map<String, AudioRef.Voice> VOICES = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static @Nullable Weather weather;
    private static @Nullable Weather tallied;
    private static Tally strikes = new Tally(0);
    private static long last;
    private static double clock;
    private static double flashedAt = Double.NEGATIVE_INFINITY;
    private static double roofedAt = Double.NEGATIVE_INFINITY;
    private static int roofX = Integer.MIN_VALUE;
    private static int roofZ = Integer.MIN_VALUE;
    private static Vector3 eye = Vector3.ZERO;
    private static WeatherLevels falling = WeatherLevels.CLEAR;
    private static boolean sheltered;

    private WeatherView() {}

    public static @Nullable Weather weather() {
        return weather;
    }

    public static WeatherLevels levels() {
        return MIX.levels();
    }

    public static WeatherLevels falling() {
        return falling;
    }

    public static Vector3 wind() {
        Weather at = weather;
        return at == null ? Vector3.ZERO : at.wind;
    }

    public static double flash() {
        return Lightning.flash(clock - flashedAt);
    }

    public static CeilingGrid roofs() {
        return ROOFS;
    }

    public static Vector3 eye() {
        return eye;
    }

    public static boolean sheltered() {
        return sheltered;
    }

    public static void frame() {
        long now = System.nanoTime();
        double seconds = last == 0 ? 0 : Math.min((now - last) / 1e9, LONGEST_FRAME);
        last = now;
        Minecraft client = Minecraft.getInstance();
        if (client.isPaused()) seconds = 0;
        clock += seconds;
        InstanceTree tree = ClientScene.tree();
        weather = tree == null ? null : Environments.weather(tree);
        CameraSnapshot camera = CameraSnapshot.current();
        if (camera != null) eye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        if (weather == null) {
            MIX.step(WeatherKind.CLEAR, 1, SETTLE, seconds);
            tallied = null;
            FLASHES.clear();
            falling = MIX.levels();
        } else {
            MIX.step(weather, seconds);
            strikes(weather, client.level);
            Lighting lighting = Environments.lighting(tree);
            falling = lighting == null ? MIX.levels() : Weathers.falling(MIX.levels(), lighting.rain, lighting.thunder);
        }
        if (tree != null && client.level != null && !falling.calm()) roofs(tree, client.level);
        sheltered = ROOFS.top(eye.x(), eye.z()) > eye.y() + 0.5;
        flashes();
        thunder();
        ambience(tree == null ? WeatherLevels.CLEAR : falling);
    }

    private static void strikes(Weather at, @Nullable ClientLevel level) {
        if (at != tallied) {
            tallied = at;
            strikes = new Tally(at.strikes);
        }
        double start = FLASHES.isEmpty() ? clock : FLASHES.getLast().at() + FLASH_SPACING;
        int count = strikes.take(at.strikes);
        for (int i = 0; i < count; i++) FLASHES.add(new Flash(start + i * FLASH_SPACING, at.strikePosition));
        for (Vector3 asked : at.takeStrikes()) struck(asked);
        for (int n = at.takeStrikesAnywhere(); n > 0; n--) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double reach = LOCAL_NEAREST + RANDOM.nextDouble() * (LOCAL_FARTHEST - LOCAL_NEAREST);
            double x = eye.x() + Math.cos(angle) * reach;
            double z = eye.z() + Math.sin(angle) * reach;
            double ground = level == null ? eye.y() : level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            struck(new Vector3(x, ground, z));
        }
    }

    private static void flashes() {
        for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext(); ) {
            Flash due = it.next();
            if (due.at() > clock) continue;
            it.remove();
            struck(due.where());
        }
    }

    private static void struck(Vector3 where) {
        flashedAt = clock;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) level.setSkyFlashTime(2);
        double distance = where.distance(eye);
        THUNDER.add(new Thunder(clock + Lightning.thunderDelay(distance), where, distance));
    }

    private static void thunder() {
        for (Iterator<Thunder> it = THUNDER.iterator(); it.hasNext(); ) {
            Thunder due = it.next();
            if (due.at() > clock) continue;
            it.remove();
            double volume = Weathers.muffled(Lightning.thunderVolume(due.distance()), sheltered);
            ResonaAudio.INSTANCE.play(Weathers.THUNDER_SOUND, sound(volume, 0.8 + RANDOM.nextDouble() * 0.2, false, null));
            if (due.distance() < CLOSE_STRIKE) ResonaAudio.INSTANCE.play(Weathers.IMPACT_SOUND, sound(Weathers.muffled(1, sheltered), 1, false, due.where()));
        }
    }

    private static void ambience(WeatherLevels levels) {
        for (Weathers.Ambience wanted : Weathers.ambience(levels, sheltered)) {
            AudioRef.Voice voice = VOICES.get(wanted.sound());
            if (voice != null && !voice.playing()) {
                VOICES.remove(wanted.sound());
                voice = null;
            }
            if (wanted.volume() <= QUIET) {
                if (voice != null) {
                    voice.fadeOut(FADE);
                    VOICES.remove(wanted.sound());
                }
                continue;
            }
            if (voice == null) {
                voice = ResonaAudio.INSTANCE.play(wanted.sound(), sound(wanted.volume(), wanted.pitch(), true, null));
                if (voice != null) VOICES.put(wanted.sound(), voice);
                continue;
            }
            voice.volume(wanted.volume());
        }
    }

    public static void stop() {
        for (AudioRef.Voice voice : VOICES.values()) voice.stop();
        VOICES.clear();
        THUNDER.clear();
        FLASHES.clear();
        MIX.reset();
        falling = WeatherLevels.CLEAR;
        weather = null;
        tallied = null;
        last = 0;
    }

    private static AudioRef.Options sound(double volume, double pitch, boolean looped, @Nullable Vector3 at) {
        return new AudioRef.Options(volume, pitch, looped, "sfx", 0, at, 8, 64, 1, looped ? FADE : 0, false);
    }

    private static void roofs(InstanceTree tree, ClientLevel level) {
        int x = (int) Math.floor(eye.x());
        int z = (int) Math.floor(eye.z());
        if (x == roofX && z == roofZ && clock - roofedAt < ROOF_EVERY) return;
        roofX = x;
        roofZ = z;
        roofedAt = clock;
        ROOFS.reset(x, z);
        for (int dz = -ROOF_REACH; dz <= ROOF_REACH; dz++) {
            for (int dx = -ROOF_REACH; dx <= ROOF_REACH; dx++) {
                ROOFS.raise(x + dx, z + dz, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
            }
        }
        for (Part part : tree.ofClass(Classes.PART)) {
            if (!part.anchored || part.transparency >= 0.99 || Instance.outOfWorld(part)) continue;
            CFrame frame = Transforms.world(part);
            Vector3 half = part.size.mul(0.5);
            Vector3 reach = abs(frame.vectorToWorld(new Vector3(half.x(), 0, 0)))
                    .add(abs(frame.vectorToWorld(new Vector3(0, half.y(), 0))))
                    .add(abs(frame.vectorToWorld(new Vector3(0, 0, half.z()))));
            Vector3 middle = frame.position();
            double top = middle.y() + reach.y();
            if (top < eye.y() - ROOF_BELOW || middle.y() - reach.y() > eye.y() + ROOF_ABOVE) continue;
            double minX = middle.x() - reach.x();
            double minZ = middle.z() - reach.z();
            double maxX = middle.x() + reach.x();
            double maxZ = middle.z() + reach.z();
            if (!ROOFS.covers(minX, minZ, maxX, maxZ)) continue;
            ROOFS.raise(minX, minZ, maxX, maxZ, top);
        }
    }

    private static Vector3 abs(Vector3 v) {
        return new Vector3(Math.abs(v.x()), Math.abs(v.y()), Math.abs(v.z()));
    }
}
