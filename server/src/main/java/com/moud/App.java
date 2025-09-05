package com.moud;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;

public class App {

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceManager.createInstanceContainer();
        minecraftServer.start("0.0.0.0", 25565);
        System.out.println("server on");
    }
}

  

