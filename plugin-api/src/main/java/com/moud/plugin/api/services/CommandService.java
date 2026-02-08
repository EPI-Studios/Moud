package com.moud.plugin.api.services;

import com.moud.plugin.api.services.commands.CommandExecutor;
import com.moud.plugin.api.services.commands.RegisteredCommand;

import java.util.Collection;

public interface CommandService {
    RegisteredCommand register(String name, CommandExecutor executor);
    RegisteredCommand register(String name, Collection<String> aliases, CommandExecutor executor);
    void unregisterAll();
}
