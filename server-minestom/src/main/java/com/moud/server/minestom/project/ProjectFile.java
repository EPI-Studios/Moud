package com.moud.server.minestom.project;

public record ProjectFile(
        int format,
        String name,
        String author
) {
    public static final int FORMAT_V1 = 1;
}

