package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.scene.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class LuauProject {

    private static final String LUAURC = LuauResource.text("project/luaurc.json");
    private static final List<String> OUTDATED_LUAURCS = List.of(
            LuauResource.text("project/luaurc-without-plugins.json"),
            LuauResource.text("project/luaurc-with-plugins.json"));
    private static final String ZED_SETTINGS = LuauResource.text("project/zed-settings.json");
    private static final String MARK = ZED_SETTINGS.substring(0, ZED_SETTINGS.indexOf('\n'));

    private LuauProject() {}

    static void write(Path place, Path types) throws IOException {
        Path luaurc = place.resolve(".luaurc");
        if (!Files.exists(luaurc) || OUTDATED_LUAURCS.contains(Files.readString(luaurc))) Files.writeString(luaurc, LUAURC);
        Path zed = place.resolve(".zed").resolve("settings.json");
        if (!Files.exists(zed) || Files.readString(zed).startsWith(MARK)) {
            Files.createDirectories(zed.getParent());
            Files.writeString(zed, zed(types.toAbsolutePath()));
        }
    }

    private static String zed(Path types) {
        String definitions = Json.write(types.toString()).strip();
        return ZED_SETTINGS.formatted(definitions);
    }
}
