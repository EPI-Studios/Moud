package com.moud.server.minestom;

import com.moud.server.minestom.util.DebugLog;
import net.minestom.server.MinecraftServer;

import java.time.Duration;

public final class MinestomServerMain {
    public static void main(String[] args) {
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
}