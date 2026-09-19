package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class EventSnippets {

    private static final String TEMPLATE = "/assets/moud/editor/snippets/event-handler.luau";
    private static final long RESCAN_MILLIS = 3000;
    private static final int MAX_FILES = 400;

    record Listener(Path file, String shown, int line) {}

    private record Scan(long at, List<Listener> found) {}

    private static String template;
    private static final Map<String, Scan> SCANS = new HashMap<>();

    private EventSnippets() {}

    static String handler(String clip, String res, AnimClip.Event event) {
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, Object> field : event.fields().entrySet()) {
            payload.append("    print(payload.").append(field.getKey()).append(") -- ").append(shown(field.getValue())).append('\n');
        }
        if (!event.table()) payload.append("    print(payload) -- ").append(shown(event.payload())).append('\n');
        else if (event.fields().isEmpty()) payload.append("    print(\"").append(event.name()).append("\")\n");
        return template().replace("{clip}", clip).replace("{res}", res).replace("{event}", event.name()).replace("{payload}", payload);
    }

    static String shown(Object value) {
        if (value instanceof Double number && number == Math.rint(number) && Math.abs(number) < 1e15) return String.valueOf(number.longValue());
        return String.valueOf(value);
    }

    private static String template() {
        if (template != null) return template;
        try (InputStream in = EventSnippets.class.getResourceAsStream(TEMPLATE)) {
            template = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MoudMod.LOG.warn("event handler template could not be read", e);
            template = "";
        }
        return template;
    }

    static List<Listener> listeners(String event) {
        long now = System.currentTimeMillis();
        Scan known = SCANS.get(event);
        if (known != null && now - known.at() < RESCAN_MILLIS) return known.found();
        List<Listener> found = scan(event);
        SCANS.put(event, new Scan(now, found));
        return found;
    }

    private static List<Listener> scan(String event) {
        Path root = AssetFiles.root();
        List<Listener> found = new ArrayList<>();
        String doubled = "getMarkerReachedSignal(\"" + event + "\")";
        String single = "getMarkerReachedSignal('" + event + "')";
        try (Stream<Path> walk = Files.walk(root, 8)) {
            List<Path> scripts = walk.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".luau"))
                    .filter(path -> !root.relativize(path).toString().startsWith(".")).limit(MAX_FILES).toList();
            for (Path script : scripts) {
                List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
                for (int n = 0; n < lines.size(); n++) {
                    String line = lines.get(n);
                    if (line.contains(doubled) || line.contains(single)) found.add(new Listener(script, root.relativize(script).toString().replace('\\', '/'), n + 1));
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return found;
    }
}
