package com.moud.server.minestom.project;

public record ProjectFile(
        int format,
        String name,
        String author,
        ProjectRuntimeSettings runtime
) {
    public static final int FORMAT_V1 = 1;

    public ProjectFile(int format, String name, String author) {
        this(format, name, author, null);
    }
}
