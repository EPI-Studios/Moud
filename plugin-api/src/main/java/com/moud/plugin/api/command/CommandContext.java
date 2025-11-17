package com.moud.plugin.api.command;

import com.moud.plugin.api.entity.Player;
import com.moud.plugin.api.player.PlayerContext;

import java.util.List;
import java.util.Optional;

public final class CommandContext {
    private final Player player;
    private final String input;
    private final List<String> arguments;

    public CommandContext(Player player, String input, List<String> arguments) {
        this.player = player;
        this.input = input;
        this.arguments = arguments;
    }

    public Player player() {
        return player;
    }

    public String input() {
        return input;
    }

    public List<String> arguments() {
        return arguments;
    }
}
