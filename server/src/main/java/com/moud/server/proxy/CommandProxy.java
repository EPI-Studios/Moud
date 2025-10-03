package com.moud.server.proxy;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CommandProxy {

    @HostAccess.Export
    public void register(String name, Value callback) {
        MinecraftServer.getCommandManager().register(new Command(name) {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player)) return;
                    callback.execute(new PlayerProxy((Player) sender));
                });
            }
        });
    }

    @HostAccess.Export
    public void registerWithAliases(String name, Value aliases, Value callback) {
        String[] aliasArray = new String[(int) aliases.getArraySize()];
        for (int i = 0; i < aliases.getArraySize(); i++) {
            aliasArray[i] = aliases.getArrayElement(i).asString();
        }

        MinecraftServer.getCommandManager().register(new Command(name, aliasArray) {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player)) return;
                    callback.execute(new PlayerProxy((Player) sender));
                });
            }
        });
    }
}