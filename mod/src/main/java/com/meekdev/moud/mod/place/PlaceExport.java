package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.place.PlaceConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class PlaceExport {

    public enum Severity { ERROR, WARNING, INFO }

    public record Issue(Severity severity, String message, @Nullable Path file, int line) {}

    private static final Pattern REFERENCE = Pattern.compile("res://[A-Za-z0-9_./ -]+?\\.[A-Za-z0-9]+(?=[\"'\\s)\\]},]|$)");
    private static final Set<String> SKIPPED = Set.of(".moud", ".git", "logs", "node_modules");
    private static final Set<String> SCANNED = Set.of(".scene", ".luau", ".rv", ".java", ".json", ".toml");
    private static final int DEPTH = 16;
    private static final long LARGE_BYTES = 200L * 1024 * 1024;

    private PlaceExport() {}

    public static List<Issue> check(Path root, List<String> scriptExtensions) {
        List<Issue> issues = new ArrayList<>();
        Path toml = root.resolve("place.toml");
        PlaceConfig config = PlaceConfig.DEFAULT;
        if (!Files.isRegularFile(toml)) {
            issues.add(new Issue(Severity.WARNING, "There is no place.toml, the place runs with default settings", null, 0));
        } else {
            try {
                config = PlaceConfig.parse(Files.readString(toml));
            } catch (IOException | IllegalArgumentException e) {
                issues.add(new Issue(Severity.ERROR, "place.toml: " + e.getMessage(), toml, 0));
            }
        }
        if (config.id().equals(PlaceConfig.DEFAULT.id())) {
            issues.add(new Issue(Severity.WARNING, "The place id is still \"" + config.id() + "\", give it its own in Project Settings", toml, 0));
        }
        entry(root, config.server(), "server", scriptExtensions, issues);
        entry(root, config.client(), "client", scriptExtensions, issues);
        if (config.scene().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "No start scene is set, players arrive in an empty world", toml, 0));
        } else if (!Files.isRegularFile(root.resolve(Res.parse(config.scene())))) {
            issues.add(new Issue(Severity.ERROR, "The start scene " + config.scene() + " does not exist", toml, 0));
        }
        references(root, issues);
        long bytes = size(root);
        if (bytes > LARGE_BYTES) {
            issues.add(new Issue(Severity.WARNING, String.format(Locale.ROOT, "The place is %.0f MB, players download all of it", bytes / 1_048_576.0), null, 0));
        }
        if (Files.isRegularFile(root.resolve("world.polar"))) {
            issues.add(new Issue(Severity.INFO, "The terrain in world.polar is included", root.resolve("world.polar"), 0));
        }
        return issues;
    }

    public static List<Path> files(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(absolute, DEPTH)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> included(absolute, file))
                    .sorted()
                    .toList();
        }
    }

    static boolean included(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIPPED.contains(part.toString())) return false;
        }
        String name = file.getFileName().toString();
        return !name.endsWith(".part") && !name.endsWith(".importing") && !name.equals(".DS_Store");
    }

    private static void entry(Path root, String res, String side, List<String> extensions, List<Issue> issues) {
        String stem;
        try {
            stem = Res.script(res);
        } catch (IllegalArgumentException e) {
            issues.add(new Issue(Severity.ERROR, "The " + side + " entry: " + e.getMessage(), root.resolve("place.toml"), 0));
            return;
        }
        int dot = stem.lastIndexOf('.');
        if (dot > stem.lastIndexOf('/')) stem = stem.substring(0, dot);
        for (String extension : extensions) {
            if (Files.isRegularFile(root.resolve(stem + "." + extension))) return;
        }
        Severity severity = side.equals("server") ? Severity.WARNING : Severity.INFO;
        issues.add(new Issue(severity, "No " + side + " script at " + res + ", that side runs nothing", null, 0));
    }

    private static void references(Path root, List<Issue> issues) {
        List<Path> scanned;
        try {
            scanned = files(root).stream().filter(PlaceExport::scanned).toList();
        } catch (IOException e) {
            return;
        }
        for (Path file : scanned) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (IOException e) {
                continue;
            }
            for (int n = 0; n < lines.size(); n++) {
                Matcher match = REFERENCE.matcher(lines.get(n));
                while (match.find()) {
                    String res = match.group();
                    try {
                        if (Files.exists(root.resolve(Res.parse(res)))) continue;
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    issues.add(new Issue(Severity.ERROR, root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()) + ":" + (n + 1) + " points at " + res + ", which is missing", file, n + 1));
                }
            }
        }
    }

    private static boolean scanned(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SCANNED.contains(name.substring(dot)) && !name.endsWith(".d.luau");
    }

    private static long size(Path root) {
        long total = 0;
        try {
            for (Path file : files(root)) total += Files.size(file);
        } catch (IOException ignored) {
        }
        return total;
    }
}
