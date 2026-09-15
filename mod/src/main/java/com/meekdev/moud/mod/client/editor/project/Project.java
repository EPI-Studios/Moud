package com.meekdev.moud.mod.client.editor.project;

import java.nio.file.Path;

public record Project(String name, Path rootDirectory, long lastOpenedMillis) {

    public static final String MARKER_FILENAME = "place.toml";

    public Path markerFile() {
        return rootDirectory.resolve(MARKER_FILENAME);
    }

    public Project withLastOpenedNow() {
        return new Project(name, rootDirectory, System.currentTimeMillis());
    }
}
