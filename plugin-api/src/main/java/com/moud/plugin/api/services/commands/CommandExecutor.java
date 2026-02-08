package com.moud.plugin.api.services.commands;

@FunctionalInterface
public interface CommandExecutor {
    void execute(CommandContext context);
}
