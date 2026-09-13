package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// a chat command a place answers, like /kick or /team. on the server
public final class ChatCommand extends Instance {

    // the words that run it, separated by spaces or commas, like "/team /t"
    public String triggers = "";

    // who typed it, the whole line, and the words after the trigger
    public record Invoked(Instance body, String text, List<String> args) {}

    public final Signal<Invoked> invoked = new Signal<>();

    public List<String> words() {
        List<String> out = new ArrayList<>();
        for (String word : triggers.split("[\\s,]+")) {
            if (word.isBlank()) continue;
            String trimmed = word.trim().toLowerCase(Locale.ROOT);
            out.add(trimmed.startsWith("/") ? trimmed.substring(1) : trimmed);
        }
        return out;
    }

    // the words after the trigger when this line runs this command, or null when it does not
    public List<String> match(String line) {
        String body = line.startsWith("/") ? line.substring(1) : line;
        String[] parts = body.trim().split("\\s+");
        if (parts.length == 0 || !words().contains(parts[0].toLowerCase(Locale.ROOT))) return null;
        List<String> args = new ArrayList<>();
        for (int n = 1; n < parts.length; n++) args.add(parts[n]);
        return args;
    }
}
