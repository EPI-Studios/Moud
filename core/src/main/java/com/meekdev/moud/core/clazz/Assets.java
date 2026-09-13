package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.asset.Res;

public final class Assets {

    private Assets() {}

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
        if (value.startsWith(Res.SCHEME)) {
            try {
                Res.parse(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + ": " + e.getMessage());
            }
            return;
        }
        if (names(value)) return;
        throw new IllegalArgumentException(where + " is \"" + value + "\", which does not name a"
                + " file. a name is namespace:path or path, in lowercase letters, digits, and"
                + " . _ - and / -- and empty, which means the engine picks");
    }
}
