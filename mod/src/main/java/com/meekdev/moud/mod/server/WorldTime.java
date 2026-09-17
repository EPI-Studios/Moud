package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.render.Daylight;
import com.meekdev.moud.core.render.Environments;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.saveddata.WeatherData;

public final class WorldTime {

    private static final double STEP = 1.0e-4;

    private static boolean driven;
    private static double clockTime = Double.NaN;
    private static double timeScale = Double.NaN;
    private static double rain = Double.NaN;
    private static double thunder = Double.NaN;

    private WorldTime() {}

    public static boolean driven() {
        return driven;
    }

    static void stopped() {
        driven = false;
        clockTime = Double.NaN;
        timeScale = Double.NaN;
        rain = Double.NaN;
        thunder = Double.NaN;
    }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Lighting lighting = Environments.lighting(ServerScene.tree());
        driven = lighting != null;
        if (lighting == null) {
            clockTime = Double.NaN;
            rate(level, MoudMod.features().isOn(Feature.DAY_NIGHT_CYCLE) ? 1 : 0);
            return;
        }
        clock(level, lighting);
        rate(level, lighting.timeScale);
        weather(server, level, lighting);
    }

    private static void clock(ServerLevel level, Lighting lighting) {
        long ticks = level.getDefaultClockTime();
        if (Double.isNaN(clockTime) || Math.abs(lighting.clockTime - clockTime) > STEP) {
            Holder<WorldClock> clock = clockOf(level);
            if (clock != null) {
                long day = Math.floorDiv(ticks, (long) Daylight.TICKS_PER_DAY);
                long moved = day * (long) Daylight.TICKS_PER_DAY + Math.round(Daylight.ticks(lighting.clockTime));
                level.clockManager().setTotalTicks(clock, moved);
            }
            clockTime = Daylight.hours(lighting.clockTime);
            return;
        }
        double running = Daylight.clockTime(ticks);
        if (Math.abs(running - lighting.clockTime) <= STEP) return;
        Instances.setNum(lighting, Classes.LIGHTING.property("clockTime"), running);
        clockTime = running;
    }

    private static void rate(ServerLevel level, double scale) {
        if (Math.abs(scale - timeScale) <= STEP) return;
        Holder<WorldClock> clock = clockOf(level);
        if (clock == null) return;
        ServerClockManager clocks = level.clockManager();
        clocks.setPaused(clock, scale <= 0);
        if (scale > 0) clocks.setRate(clock, (float) scale);
        timeScale = scale;
    }

    private static void weather(MinecraftServer server, ServerLevel level, Lighting lighting) {
        double wet = Math.clamp(lighting.rain, 0, 1);
        double storm = Math.clamp(lighting.thunder, 0, 1);
        if (Math.abs(wet - rain) <= STEP && Math.abs(storm - thunder) <= STEP) return;
        boolean wasWet = rain > 0;
        rain = wet;
        thunder = storm;
        WeatherData weather = server.getWeatherData();
        weather.setRaining(wet > 0);
        weather.setThundering(storm > 0);
        level.setRainLevel((float) wet);
        level.setThunderLevel((float) storm);
        if (wasWet != wet > 0) {
            server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(
                    wet > 0 ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        }
        server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, (float) wet));
        server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, (float) storm));
    }

    private static Holder<WorldClock> clockOf(ServerLevel level) {
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) return clock.get();
        Optional<Holder.Reference<WorldClock>> overworld = level.registryAccess().get(WorldClocks.OVERWORLD);
        return overworld.isPresent() ? overworld.get() : null;
    }
}
