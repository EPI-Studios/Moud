package com.meekdev.moud.core.chat;

import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.meekdev.moud.core.instance.Instance;

public final class ChatCommand extends Instance {

    public String triggers = "";

    public record Invoked(Instance body, String text, List<String> args) {}

    public final Signal<Invoked> invoked = new Signal<>();

    public List<String> triggerWords() {
        List<String> out = new ArrayList<>();
        for (String word : triggers.split("[\\s,]+")) {
            if (word.isBlank()) continue;
            String trimmed = word.trim().toLowerCase(Locale.ROOT);
            out.add(trimmed.startsWith("/") ? trimmed.substring(1) : trimmed);
        }
        return out;
    }

    public List<String> match(String line) {
        String body = line.startsWith("/") ? line.substring(1) : line;
        String[] parts = body.trim().split("\\s+");
        if (parts.length == 0 || !triggerWords().contains(parts[0].toLowerCase(Locale.ROOT))) return null;
        List<String> args = new ArrayList<>();
        for (int n = 1; n < parts.length; n++) args.add(parts[n]);
        return args;
    }
}
