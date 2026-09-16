package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.SpawnLocation;
import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.core.tween.Easing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ProjectStore {

    private static final String PROJECTS_KEY = "projects";
    private static final String PINNED_KEY = "pinned";
    private static final String TEMPLATES = "/assets/moud/templates/";
    private static final List<String> ENTRY_FILES = List.of("server/main.luau", "client/main.luau", "server/main.rv", "server/Main.java");
    private static final Vector3 BASEPLATE_SIZE = new Vector3(128, 1, 128);
    private static final CFrame BASEPLATE_CFRAME = CFrame.at(0, 63.5, 0);
    private static final Color BASEPLATE_COLOR = new Color(0.39f, 0.40f, 0.42f);
    private static final CFrame MENU_SPAWN = CFrame.at(0, 64, 0);
    private static final double MENU_ORBIT_SECONDS = 30;
    private static final int MENU_ORBIT_POINTS = 4;
    private static final double MENU_ORBIT_RADIUS = 30;
    private static final double MENU_ORBIT_HEIGHT = 74;
    private static final double MENU_ORBIT_BOB = 6;

    private final Path recentsFile;

    public ProjectStore(Path recentsFile) {
        this.recentsFile = recentsFile;
    }

    public static Path defaultRecentsFile() {
        return Path.of(System.getProperty("user.home"), ".moud", "recents.json");
    }

    public static Path defaultProjectsFolder() {
        return Path.of(System.getProperty("user.home"), "Moud");
    }

    public List<Project> loadRecents() {
        List<Project> projects = new ArrayList<>();
        if (!(document().get(PROJECTS_KEY) instanceof List<?> entries)) return projects;
        for (Object raw : entries) {
            if (!(raw instanceof Map<?, ?> entry) || !(entry.get("path") instanceof String path)) continue;
            long opened = entry.get("lastOpenedMillis") instanceof Number number ? number.longValue() : 0L;
            Path root = Path.of(path);
            if (!Files.isDirectory(root)) continue;
            readProjectFromDisk(root, opened).ifPresent(projects::add);
        }
        return projects;
    }

    public Set<String> loadPinnedPaths() {
        Set<String> pinned = new LinkedHashSet<>();
        if (document().get(PINNED_KEY) instanceof List<?> paths) {
            for (Object path : paths) if (path instanceof String text) pinned.add(text);
        }
        return pinned;
    }

    public Optional<Project> readProjectFromDisk(Path root, long lastOpenedMillis) {
        Path marker = root.resolve(Project.MARKER_FILENAME);
        String folderName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        if (Files.isRegularFile(marker)) {
            try {
                return Optional.of(new Project(PlaceConfig.parse(Files.readString(marker)).name(), root, lastOpenedMillis));
            } catch (IOException | IllegalArgumentException e) {
                return Optional.of(new Project(folderName, root, lastOpenedMillis));
            }
        }
        boolean hasEntry = ENTRY_FILES.stream().anyMatch(file -> Files.isRegularFile(root.resolve(file)));
        return hasEntry ? Optional.of(new Project(folderName, root, lastOpenedMillis)) : Optional.empty();
    }

    public void recordOpened(Project project) throws IOException {
        List<Project> recents = loadRecents();
        recents.removeIf(existing -> same(existing.rootDirectory(), project.rootDirectory()));
        recents.addFirst(project.withLastOpenedNow());
        save(recents, loadPinnedPaths());
    }

    public void removeRecent(Path root) throws IOException {
        List<Project> recents = loadRecents();
        recents.removeIf(existing -> same(existing.rootDirectory(), root));
        Set<String> pinned = loadPinnedPaths();
        pinned.remove(absolute(root));
        save(recents, pinned);
    }

    public void setPinned(Path root, boolean pinned) throws IOException {
        Set<String> paths = loadPinnedPaths();
        if (pinned) paths.add(absolute(root));
        else paths.remove(absolute(root));
        save(loadRecents(), paths);
    }

    public void clearRecents() throws IOException {
        save(List.of(), Set.of());
    }

    public enum Template { BASEPLATE, MENU, EMPTY }

    public Project createProject(String name, Path root) throws IOException {
        return createProject(name, root, Template.BASEPLATE);
    }

    public Project createProject(String name, Path root, Template template) throws IOException {
        if (Files.exists(root) && !isEmpty(root)) throw new IOException("Target directory is not empty: " + root);
        Files.createDirectories(root.resolve("scenes"));
        Files.createDirectories(root.resolve("server"));
        Files.createDirectories(root.resolve("client"));
        if (template == Template.MENU) {
            Files.writeString(root.resolve(Project.MARKER_FILENAME), placeToml(name) + "\n" + readResource(TEMPLATES + "menu/settings.toml"));
            copyTemplate("menu", root);
            Files.writeString(root.resolve("scenes/main.scene"), menuScene());
            return new Project(name, root, System.currentTimeMillis());
        }
        Files.writeString(root.resolve(Project.MARKER_FILENAME), placeToml(name));
        copyResource(TEMPLATES + "starter/server/main.luau", root.resolve("server/main.luau"));
        copyResource(TEMPLATES + "starter/client/main.luau", root.resolve("client/main.luau"));
        Files.writeString(root.resolve("scenes/main.scene"), template == Template.BASEPLATE ? starterScene() : Scene.save(List.of()));
        return new Project(name, root, System.currentTimeMillis());
    }

    private static String placeToml(String name) throws IOException {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String escaped = name.replace("\\", "\\\\").replace("\"", "\\\"");
        return readResource(TEMPLATES + "place.toml")
                .replace("{id}", id.isEmpty() ? "place" : id)
                .replace("{name}", escaped);
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = ProjectStore.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = ProjectStore.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " is missing");
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        }
    }

    private static void copyTemplate(String template, Path root) throws IOException {
        String base = TEMPLATES + template + "/";
        List<String> files = readResource(base + "files.txt").lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        for (String file : files) copyResource(base + file, root.resolve(file));
        copyResource(base + "luaurc.json", root.resolve(".luaurc"));
    }

    private static String menuScene() {
        InstanceTree tree = new InstanceTree();
        var world = Instances.createRoot(tree, Classes.FOLDER, "World");
        Part baseplate = baseplate(world);
        SpawnLocation start = Instances.create(Classes.SPAWN_LOCATION, world, "Start");
        start.cframe = MENU_SPAWN;
        CameraPath orbit = Instances.create(Classes.CAMERA_PATH, world, "MenuOrbit");
        orbit.duration = MENU_ORBIT_SECONDS;
        orbit.easing = Easing.LINEAR;
        orbit.looped = true;
        orbit.closed = true;
        orbit.lookAt = start;
        for (int n = 1; n <= MENU_ORBIT_POINTS; n++) {
            double angle = (double) n / MENU_ORBIT_POINTS * Math.PI * 2;
            double height = MENU_ORBIT_HEIGHT + (n % 2) * MENU_ORBIT_BOB;
            Attachment point = Instances.create(Classes.ATTACHMENT, orbit, "Point" + n);
            point.cframe = CFrame.at(Math.cos(angle) * MENU_ORBIT_RADIUS, height, Math.sin(angle) * MENU_ORBIT_RADIUS);
        }
        Remote menu = Instances.create(Classes.REMOTE, world, "Menu");
        menu.accepts = "string";
        return Scene.save(List.of(baseplate, start, orbit, menu));
    }

    private static String starterScene() {
        InstanceTree tree = new InstanceTree();
        var world = Instances.createRoot(tree, Classes.FOLDER, "World");
        return Scene.save(List.of(baseplate(world)));
    }

    private static Part baseplate(Instance world) {
        Part baseplate = Instances.create(Classes.PART, world, "Baseplate");
        baseplate.size = BASEPLATE_SIZE;
        baseplate.cframe = BASEPLATE_CFRAME;
        baseplate.color = BASEPLATE_COLOR;
        return baseplate;
    }

    private Map<?, ?> document() {
        if (!Files.isRegularFile(recentsFile)) return Map.of();
        try {
            return Json.parse(Files.readString(recentsFile)) instanceof Map<?, ?> map ? map : Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private void save(List<Project> projects, Set<String> pinned) throws IOException {
        Files.createDirectories(recentsFile.getParent());
        List<Object> entries = new ArrayList<>();
        for (Project project : projects) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", absolute(project.rootDirectory()));
            entry.put("lastOpenedMillis", (double) project.lastOpenedMillis());
            entries.add(entry);
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(PROJECTS_KEY, entries);
        document.put(PINNED_KEY, new ArrayList<>(pinned));
        Files.writeString(recentsFile, Json.write(document));
    }

    private static boolean same(Path a, Path b) {
        return absolute(a).equals(absolute(b));
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        }
    }
}
