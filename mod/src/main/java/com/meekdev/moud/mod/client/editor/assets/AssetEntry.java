package com.meekdev.moud.mod.client.editor.assets;

import java.nio.file.Path;
import java.util.Locale;

public record AssetEntry(String name, Path path, AssetKind kind, long size, long modified) {

    public boolean folder() {
        return kind == AssetKind.FOLDER;
    }

    public String formattedSize() {
        if (folder()) return "";
        if (size < 1024) return size + " B";
        String[] units = {"KB", "MB", "GB"};
        double value = size;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
