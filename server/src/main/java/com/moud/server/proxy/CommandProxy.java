package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@TsExpose
public class CommandProxy {

    @HostAccess.Export
    public void register(String name, Value callback) {

        var argsArgument = ArgumentType.StringArray("args");

        var command = new Command(name);

        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player)) return;

            callback.execute(new PlayerProxy((Player) sender), new String[0]);
        });

        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player)) return;
            final String[] args = context.get(argsArgument);
            callback.execute(new PlayerProxy((Player) sender), args);
        }, argsArgument);

        MinecraftServer.getCommandManager().register(command);
    }

    @HostAccess.Export
    public void registerWithAliases(String name, Value aliases, Value callback) {
        String[] aliasArray = new String[(int) aliases.getArraySize()];
        for (int i = 0; i < aliases.getArraySize(); i++) {
            aliasArray[i] = aliases.getArrayElement(i).asString();
        }

        var argsArgument = ArgumentType.StringArray("args");
        var command = new Command(name, aliasArray);

        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player)) return;
            callback.execute(new PlayerProxy((Player) sender), new String[0]);
        });

        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player)) return;
            final String[] args = context.get(argsArgument);
            callback.execute(new PlayerProxy((Player) sender), args);
        }, argsArgument);

        MinecraftServer.getCommandManager().register(command);
    }
}