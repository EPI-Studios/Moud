package com.moud.server.plugin.impl;

import com.moud.plugin.api.services.CommandService;
import com.moud.plugin.api.services.commands.CommandContext;
import com.moud.plugin.api.services.commands.CommandExecutor;
import com.moud.plugin.api.services.commands.RegisteredCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandServiceImpl implements CommandService {
    private final Logger logger;
    private final Set<PluginCommandHandle> commands = ConcurrentHashMap.newKeySet();

    public CommandServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public RegisteredCommand register(String name, CommandExecutor executor) {
        return register(name, List.of(), executor);
    }

    @Override
    public RegisteredCommand register(String name, Collection<String> aliases, CommandExecutor executor) {
        ArgumentStringArray argsArgument = ArgumentType.StringArray("args");
        String[] aliasArray = aliases == null ? new String[0] : aliases.toArray(new String[0]);
        Command command = new Command(name, aliasArray);

        command.setDefaultExecutor((sender, context) ->
                executor.execute(new CommandContext(sender, name, List.of())));

        command.addSyntax((sender, context) -> {
            String[] args = context.get(argsArgument);
            executor.execute(new CommandContext(sender, name, List.of(args)));
        }, argsArgument);

        MinecraftServer.getCommandManager().register(command);
        PluginCommandHandle handle = new PluginCommandHandle(command, this);
        commands.add(handle);
        logger.info("Registered plugin command {}", name);
        return handle;
    }

    void remove(PluginCommandHandle handle) {
        commands.remove(handle);
    }

    @Override
    public void unregisterAll() {
        commands.forEach(PluginCommandHandle::unregister);
        commands.clear();
    }

    private static final class PluginCommandHandle implements RegisteredCommand {
        private final Command command;
        private final CommandServiceImpl owner;
        private volatile boolean active = true;

        private PluginCommandHandle(Command command, CommandServiceImpl owner) {
            this.command = command;
            this.owner = owner;
        }

        @Override
        public String name() {
            return command.getName();
        }

        @Override
        public void unregister() {
            if (!active) {
                return;
            }
            active = false;
            MinecraftServer.getCommandManager().unregister(command);
            owner.remove(this);
        }
    }
}
