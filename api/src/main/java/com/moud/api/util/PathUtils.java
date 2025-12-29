package com.moud.api.util;

public final class PathUtils {
    private PathUtils() {
    }

    public static String normalizeSlashes(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }
}
