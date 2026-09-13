package com.meekdev.moud.core.asset;

// a res:// path, checked and stripped down to where it sits under the place directory
public final class Res {

    public static final String SCHEME = "res://";

    private Res() {}

    public static String parse(String text) {
        if (!text.startsWith(SCHEME)) {
            throw new IllegalArgumentException("\"" + text + "\" is not a res:// path. every file in a"
                    + " place is named from the place directory, like res://server/lib/util.luau");
        }
        String path = text.substring(SCHEME.length());
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("\"" + text + "\" has to name a file under the place,"
                    + " with / between folders and nothing before the first one");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("\"" + text + "\" has an empty, . or .. folder in"
                        + " it. a path cannot leave the place or say the same folder twice");
            }
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.indexOf('.') <= 0) {
            throw new IllegalArgumentException("\"" + text + "\" has no extension. it is always"
                    + " written, so the name is the file");
        }
        return path;
    }
}
