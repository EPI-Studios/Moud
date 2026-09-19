package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Environments;
import com.meekdev.moud.core.render.Lightning;
import com.meekdev.moud.core.render.Weather;
import com.meekdev.moud.core.render.WeatherKind;
import com.meekdev.moud.core.render.WeatherLevels;
import com.meekdev.moud.core.render.WeatherMix;
import com.meekdev.moud.mod.MoudMod;
import java.util.List;
import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public final class ServerWeather {

    private static final double NEAREST = 16;
    private static final double FARTHEST = 72;
    private static final double SETTLE = 3;

    private static final WeatherMix MIX = new WeatherMix();
    private static final Random RANDOM = new Random();
    private static @Nullable Weather weather;

    private ServerWeather() {}

    public static @Nullable Weather weather() {
        return weather;
    }

    public static WeatherLevels levels() {
        return MIX.levels();
    }

    static void stopped() {
        MIX.reset();
        weather = null;
    }

    static void tick(MinecraftServer server, boolean playing, double dt) {
        InstanceTree tree = ServerScene.tree();
        weather = tree == null ? null : Environments.weather(tree);
        if (weather == null) {
            MIX.step(WeatherKind.CLEAR, 1, SETTLE, dt);
            return;
        }
        WeatherLevels levels = MIX.step(weather, dt);
        for (Vector3 at : weather.takeStrikes()) strike(server, weather, at);
        for (int n = weather.takeStrikesAnywhere(); n > 0; n--) strike(server, weather, null);
        if (playing && Lightning.due(levels.storm(), dt, RANDOM.nextDouble())) strike(server, weather, null);
    }

    private static void strike(MinecraftServer server, Weather at, @Nullable Vector3 asked) {
        Vector3 where = asked != null ? asked : somewhere(server);
        Instances.setObj(at, Classes.WEATHER.property("strikePosition"), where);
        Instances.setNum(at, Classes.WEATHER.property("strikes"), (double) at.strikes + 1);
        try {
            at.struck.fire(where);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("a struck handler failed", e);
        }
    }

    private static Vector3 somewhere(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        double x = 0;
        double z = 0;
        if (!players.isEmpty()) {
            ServerPlayer near = players.get(RANDOM.nextInt(players.size()));
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double reach = NEAREST + RANDOM.nextDouble() * (FARTHEST - NEAREST);
            x = near.getX() + Math.cos(angle) * reach;
            z = near.getZ() + Math.sin(angle) * reach;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        return new Vector3(x, ground, z);
    }
}
