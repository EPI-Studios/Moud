package com.moud.core.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NodePath {
    private final boolean absolute;
    private final List<String> parts;

    private NodePath(boolean absolute, List<String> parts) {
        this.absolute = absolute;
        this.parts = parts;
    }

    public static NodePath parse(String raw) {
        Objects.requireNonNull(raw);
        String input = raw.trim();
        if (input.isEmpty()) {
            throw new IllegalArgumentException("Empty NodePath");
        }
        boolean absolute = input.startsWith("/");
        String[] tokens = input.split("/");

        List<String> parts = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            parts.add(token);
        }

        return new NodePath(absolute, Collections.unmodifiableList(parts));
    }

    public boolean absolute() {
        return absolute;
    }

    public List<String> parts() {
        return parts;
    }
}

