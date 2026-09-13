package com.meekdev.moud.core.asset;

public final class Res {

    public static final String SCHEME = "res://";

    private Res() {}

    public static String parse(String text) {
        String path = script(text);
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.indexOf('.') <= 0) {
            throw new IllegalArgumentException("\"" + text + "\" has no file extension");
        }
        return path;
    }

    public static String script(String text) {
        if (!text.startsWith(SCHEME)) {
            throw new IllegalArgumentException("\"" + text + "\" is not a res:// path");
        }
        String path = text.substring(SCHEME.length());
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("invalid res:// path \"" + text + "\"");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("\"" + text + "\" contains an empty, . or .. segment");
            }
        }
        return path;
    }
}
