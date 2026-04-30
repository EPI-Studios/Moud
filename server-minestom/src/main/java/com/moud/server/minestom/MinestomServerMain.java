package com.moud.server.minestom;

import com.moud.server.minestom.util.DebugLog;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;

import java.time.Duration;
import java.lang.reflect.Field;

public final class MinestomServerMain {

    private static final int TARGET_TICKS_PER_SECOND = 60;

    public static void main(String[] args) {
        applyTickRate(TARGET_TICKS_PER_SECOND);
        MinecraftServer server = MinecraftServer.init();

        MoudServer moud = MoudServer.fromEnvironment().build();
        moud.register(
                MinecraftServer.getGlobalEventHandler(),
                MinecraftServer.getInstanceManager()
        );
        moud.registerTickTask();

        MinecraftServer.getSchedulerManager()
                .buildTask(() -> DebugLog.info("server", "listening on :25565"))
                .delay(Duration.ofMillis(100))
                .schedule();

        server.start("0.0.0.0", 25565);
    }

    private static void applyTickRate(int ticksPerSecond) {
        try {
            System.setProperty("minestom.tps", Integer.toString(ticksPerSecond));
            Field tpsField = ServerFlag.class.getField("SERVER_TICKS_PER_SECOND");
            if (!tpsField.canAccess(null)) {
                tpsField.setAccessible(true);
            }
            tpsField.set(null, ticksPerSecond);
            DebugLog.info("server", "tick rate set to " + ticksPerSecond + " Hz");
        } catch (Throwable t) {
            DebugLog.info("server", "could not raise tick rate, using Minestom default: " + t.getMessage());
        }
    }
}