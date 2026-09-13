package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.asset.Res;

// what a property that names a file is allowed to hold
//
// the check is here, in core, rather than at the renderer that resolves it, because the renderer runs
// per frame: a bad name there can only be a log line or a crash, and the frame after it is the same
// bad name again. at the write it is one error, at the moment somebody typed it, naming what was
// allowed -- rule 8.3.6
public final class Assets {

    private Assets() {}

    // the shape minecraft's own resource names have, which is what these resolve to: a namespace and
    // a path, or a path on its own. the charset is theirs too, so a name that passes here is a name
    // the game will take
    public static boolean names(String value) {
        int colon = value.indexOf(':');
        if (colon == 0 || colon != value.lastIndexOf(':')) return false;
        String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (path.isEmpty()) return false;
        for (int n = 0; n < namespace.length(); n++) {
            if (!plain(namespace.charAt(n), false)) return false;
        }
        for (int n = 0; n < path.length(); n++) {
            if (!plain(path.charAt(n), true)) return false;
        }
        return true;
    }

    private static boolean plain(char c, boolean inPath) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '_' || c == '.' || c == '-' || inPath && c == '/';
    }

    public static void check(String where, String value) {
        if (value.isEmpty()) return;
        // a file in the place, which has its own rules and its own message
        if (value.startsWith(Res.SCHEME)) {
            try {
                Res.parse(value);
            } catch (IllegalArgumentException wrong) {
                throw new IllegalArgumentException(where + ": " + wrong.getMessage());
            }
            return;
        }
        if (names(value)) return;
        throw new IllegalArgumentException(where + " is \"" + value + "\", which does not name a"
                + " file. a name is namespace:path or path, in lowercase letters, digits, and"
                + " . _ - and / -- and empty, which means the engine picks");
    }
}
