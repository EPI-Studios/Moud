package com.moud.plugin.api.command;

import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.entity.Player;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.CommandService;
import com.moud.plugin.api.services.commands.CommandExecutor;
import com.moud.plugin.api.services.commands.RegisteredCommand;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class CommandDsl {

    private final PluginContext context;
    private final String name;
    private Consumer<CommandContext> executor;
    private Collection<String> aliases = List.of();
    private String description;

    public CommandDsl(PluginContext context, String name) {
        this.context = context;
        this.name = name;
    }

    public CommandDsl aliases(Collection<String> aliases) {
        this.aliases = aliases != null ? aliases : List.of();
        return this;
    }

    public CommandDsl executor(Consumer<CommandContext> executor) {
        this.executor = executor;
        return this;
    }

    public CommandDsl description(String description) {
        this.description = description;
        return this;
    }

    public RegisteredCommand register() {
        Objects.requireNonNull(executor, "Command executor must be provided");
        CommandService service = context.commands();
        CommandExecutor adapter = raw -> {
            Player dslPlayer = null;
            if (raw.sender() instanceof net.minestom.server.entity.Player minestomPlayer) {
                PlayerContext playerContext = new PlayerContext() {
                    @Override
                    public UUID uuid() {
                        return minestomPlayer.getUuid();
                    }

                    @Override
                    public String username() {
                        return minestomPlayer.getUsername();
                    }

                    @Override
                    public net.minestom.server.entity.Player player() {
                        return minestomPlayer;
                    }
                };
                dslPlayer = Player.wrap(context, playerContext);
            }
            List<String> args = raw.arguments() != null ? raw.arguments() : List.of();
            executor.accept(new CommandContext(dslPlayer, raw.input(), args));
        };
        return service.register(name, aliases, adapter);
    }
}
