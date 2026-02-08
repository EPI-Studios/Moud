package com.moud.plugin.api.services.commands;

import net.minestom.server.command.CommandSender;

import java.util.List;

public record CommandContext(CommandSender sender,
                             String input,
                             List<String> arguments) {
}
