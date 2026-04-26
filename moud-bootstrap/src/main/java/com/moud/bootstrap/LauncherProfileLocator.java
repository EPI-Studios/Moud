package com.moud.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LauncherProfileLocator {

    enum LauncherType {
        VANILLA,
        CURSEFORGE,
        PRISM,
        MULTIMC,
        MODRINTH_THESEUS,
        UNKNOWN
    }

    record LauncherProfile(LauncherType type, Path file, Path gameDir) {
    }

    LauncherProfile detect(Path gameDir) {
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();

        Path curseForge = findFirstRegularFile(curseForgeLauncherProfiles());
        if (curseForge != null) {
            return new LauncherProfile(LauncherType.CURSEFORGE, curseForge, normalizedGameDir);
        }

        Path vanilla = normalizedGameDir.getParent() != null
                ? normalizedGameDir.getParent().resolve("launcher_profiles.json")
                : null;
        if (isRegularFile(vanilla)) {
            return new LauncherProfile(LauncherType.VANILLA, vanilla, normalizedGameDir);
        }

        Path instanceCfg = normalizedGameDir.getParent() != null
                ? normalizedGameDir.getParent().resolve("instance.cfg")
                : null;
        if (isRegularFile(instanceCfg)) {
            return new LauncherProfile(detectPrismLikeType(instanceCfg), instanceCfg, normalizedGameDir);
        }

        Path directTheseus = normalizedGameDir.getParent() != null
                ? normalizedGameDir.getParent().resolve("profile.json")
                : null;
        if (isRegularFile(directTheseus)) {
            return new LauncherProfile(LauncherType.MODRINTH_THESEUS, directTheseus, normalizedGameDir);
        }

        Path scannedTheseus = findTheseusProfile(normalizedGameDir);
        if (scannedTheseus != null) {
            return new LauncherProfile(LauncherType.MODRINTH_THESEUS, scannedTheseus, normalizedGameDir);
        }

        return new LauncherProfile(LauncherType.UNKNOWN, null, normalizedGameDir);
    }

    private static List<Path> curseForgeLauncherProfiles() {
        ArrayList<Path> paths = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home", "");

        if (appData != null && !appData.isBlank()) {
            paths.add(Path.of(appData, "CurseForge", "minecraft", "Install", "launcher_profiles.json"));
        }
        if (!home.isBlank()) {
            paths.add(Path.of(home, "Library", "Application Support", "CurseForge", "minecraft", "Install", "launcher_profiles.json"));
            paths.add(Path.of(home, ".config", "CurseForge", "minecraft", "Install", "launcher_profiles.json"));
        }

        return List.copyOf(paths);
    }

    private static LauncherType detectPrismLikeType(Path instanceCfg) {
        Path current = instanceCfg.toAbsolutePath().normalize();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null) {
                String lower = name.toString().toLowerCase(Locale.ROOT);
                if (lower.contains("multimc")) return LauncherType.MULTIMC;
                if (lower.contains("prism")) return LauncherType.PRISM;
            }
            current = current.getParent();
        }
        return LauncherType.PRISM;
    }

    private static Path findTheseusProfile(Path gameDir) {
        for (Path base : theseusProfileBases()) {
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (var profiles = Files.list(base)) {
                Path match = profiles
                        .map(path -> path.resolve("profile.json"))
                        .filter(LauncherProfileLocator::isRegularFile)
                        .filter(path -> profileContainsGameDir(path, gameDir))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    return match;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static List<Path> theseusProfileBases() {
        ArrayList<Path> paths = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home", "");

        if (appData != null && !appData.isBlank()) {
            paths.add(Path.of(appData, "com.modrinth.theseus", "profiles"));
        }
        if (!home.isBlank()) {
            paths.add(Path.of(home, ".local", "share", "com.modrinth.theseus", "profiles"));
            paths.add(Path.of(home, "Library", "Application Support", "com.modrinth.theseus", "profiles"));
        }

        return List.copyOf(paths);
    }

    private static boolean profileContainsGameDir(Path profileJson, Path gameDir) {
        try {
            String normalizedGameDir = gameDir.toAbsolutePath().normalize().toString();
            String raw = Files.readString(profileJson);
            return raw.contains(normalizedGameDir) || raw.contains(normalizedGameDir.replace("\\", "\\\\"));
        } catch (IOException e) {
            return false;
        }
    }

    private static Path findFirstRegularFile(List<Path> candidates) {
        for (Path candidate : candidates) {
            if (isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isRegularFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }
}
