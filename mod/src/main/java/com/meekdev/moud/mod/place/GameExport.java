package com.meekdev.moud.mod.place;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jspecify.annotations.Nullable;

public final class GameExport {

    public record Progress(String step, int done, int total) {}

    public record Result(Path jar, long bytes, List<String> included) {}

    private static final String EDITOR = "com/meekdev/moud/mod/client/editor/";
    private static final String EDITOR_ASSETS = "assets/moud/editor/";
    private static final String TABLES = "/assets/moud/export.json";
    private static final JsonObject TABLE = readTables();
    private static final Set<String> COMPANION_MODS = Set.copyOf(strings("companionMods"));
    private static final Pattern LIBRARY = Pattern.compile("(" + String.join("|", strings("libraries")) + ")-\\d.*\\.jar");
    private static final Pattern SIGNATURE = Pattern.compile("META-INF/[^/]+\\.(SF|RSA|DSA|EC)");
    private static final int DEPTH = 32;
    private static final List<String> MARKERS = strings("markers");

    private GameExport() {}

    public record Plan(List<String> mods, int files, long bytes) {}

    public static Plan plan(Path root) {
        List<String> mods = new ArrayList<>();
        mods.add("Moud engine");
        for (ModContainer mod : companions()) mods.add(mod.getMetadata().getName());
        int files = 0;
        long bytes = 0;
        try {
            for (Path file : PlaceExport.files(root)) {
                files++;
                bytes += Files.size(file);
            }
        } catch (IOException ignored) {
        }
        return new Plan(mods, files, bytes);
    }

    public static Result build(Path root, PlaceConfig config, Path target, Consumer<Progress> progress, AtomicBoolean cancel) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        List<String> included = new ArrayList<>();
        try (OutputStream file = Files.newOutputStream(temporary); JarOutputStream jar = new JarOutputStream(file, manifest(config))) {
            Writer writer = new Writer(jar);
            ModContainer moud = FabricLoader.getInstance().getModContainer("moud").orElseThrow(() -> new IOException("the moud mod is not loaded"));
            List<Path> engine = engine(moud);
            boolean packaged = engine.size() == 1 && Files.isRegularFile(engine.getFirst());
            progress.accept(new Progress("Copying the engine", 0, 4));
            for (Path source : engine) copy(source, writer, cancel, true);
            included.add("Moud engine without the editor");
            if (!packaged) {
                progress.accept(new Progress("Copying libraries", 1, 4));
                for (Path library : libraries(engine)) {
                    copy(library, writer, cancel, false);
                    included.add(library.getFileName().toString());
                }
            }
            progress.accept(new Progress("Adding companion mods", 2, 4));
            List<String> nested = new ArrayList<>(nestedFrom(engine, packaged));
            for (ModContainer mod : companions()) {
                if (cancel.get()) throw cancelled();
                String name = "META-INF/jars/" + mod.getMetadata().getId() + "-" + mod.getMetadata().getVersion().getFriendlyString() + ".jar";
                writer.put(name, jarOf(mod.getOrigin().getPaths()));
                nested.add(name);
                included.add(mod.getMetadata().getName() + " " + mod.getMetadata().getVersion().getFriendlyString());
            }
            progress.accept(new Progress("Packing the place", 3, 4));
            List<Path> files = PlaceExport.files(root);
            MessageDigest digest = sha256();
            StringBuilder listing = new StringBuilder();
            Path absolute = root.toAbsolutePath().normalize();
            for (Path placeFile : files) {
                if (cancel.get()) throw cancelled();
                String relative = absolute.relativize(placeFile.toAbsolutePath().normalize()).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(placeFile);
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update(bytes);
                writer.put(Game.PLACE + relative, bytes);
                listing.append(relative).append('\n');
            }
            included.add(files.size() + " place files");
            writer.put(Game.FILES, listing.toString().getBytes(StandardCharsets.UTF_8));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", config.id());
            info.put("name", config.name());
            info.put("version", config.version());
            info.put("hash", HexFormat.of().formatHex(digest.digest()));
            writer.put(Game.INFO, Json.write(info).getBytes(StandardCharsets.UTF_8));
            writer.put("fabric.mod.json", modJson(engine, config, nested).getBytes(StandardCharsets.UTF_8));
            writer.finish();
            progress.accept(new Progress("Writing the jar", 4, 4));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return new Result(target, Files.size(target), included);
    }

    private static final class Writer {
        private final JarOutputStream jar;
        private final Set<String> written = new LinkedHashSet<>();
        private final Map<String, ByteArrayOutputStream> services = new LinkedHashMap<>();

        Writer(JarOutputStream jar) {
            this.jar = jar;
        }

        void put(String name, byte[] bytes) throws IOException {
            if (name.startsWith("META-INF/services/")) {
                services.computeIfAbsent(name, key -> new ByteArrayOutputStream()).write(bytes);
                services.get(name).write('\n');
                return;
            }
            if (!written.add(name)) return;
            JarEntry entry = new JarEntry(name);
            jar.putNextEntry(entry);
            jar.write(bytes);
            jar.closeEntry();
        }

        void finish() throws IOException {
            for (Map.Entry<String, ByteArrayOutputStream> service : services.entrySet()) {
                if (!written.add(service.getKey())) continue;
                jar.putNextEntry(new JarEntry(service.getKey()));
                jar.write(service.getValue().toByteArray());
                jar.closeEntry();
            }
            services.clear();
        }
    }

    private static void copy(Path source, Writer writer, AtomicBoolean cancel, boolean engine) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> walk = Files.walk(source, DEPTH)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (cancel.get()) throw cancelled();
                    String name = source.relativize(file).toString().replace(File.separatorChar, '/');
                    if (keep(name, engine)) writer.put(name, Files.readAllBytes(file));
                }
            }
        } else if (Files.isRegularFile(source)) {
            try (ZipFile zip = new ZipFile(source.toFile())) {
                for (ZipEntry entry : zip.stream().filter(e -> !e.isDirectory()).toList()) {
                    if (cancel.get()) throw cancelled();
                    if (!keep(entry.getName(), engine)) continue;
                    if (engine && entry.getName().startsWith("META-INF/jars/") && entry.getName().toLowerCase(Locale.ROOT).contains("imgui")) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        writer.put(entry.getName(), in.readAllBytes());
                    }
                }
            }
        }
    }

    private static boolean keep(String name, boolean engine) {
        if (name.equals("META-INF/MANIFEST.MF") || name.endsWith("module-info.class") || SIGNATURE.matcher(name).matches()) return false;
        if (name.equals("fabric.mod.json")) return false;
        if (name.startsWith(Game.FOLDER + "/")) return false;
        return !engine || !name.startsWith(EDITOR) && !name.startsWith(EDITOR_ASSETS);
    }

    private static List<Path> libraries(List<Path> engine) {
        Set<Path> found = new LinkedHashSet<>();
        for (String marker : MARKERS) {
            Path location = location(marker);
            if (location == null || engine.contains(location) || companionOrigin(location)) continue;
            found.add(location);
            Path resources = resourcesBeside(location);
            if (resources != null) found.add(resources);
        }
        Path projectRoot = projectRoot(engine);
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (engine.contains(path)) continue;
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            boolean library = Files.isRegularFile(path) && LIBRARY.matcher(name).matches();
            boolean project = projectRoot != null && Files.isDirectory(path) && Stream.of("core", "script", "net")
                    .anyMatch(module -> path.startsWith(projectRoot.resolve(module).resolve("build")));
            if (library || project) found.add(path);
        }
        return List.copyOf(found);
    }

    private static List<Path> engine(ModContainer moud) {
        Set<Path> paths = new LinkedHashSet<>();
        for (Path path : moud.getOrigin().getPaths()) paths.add(path.toAbsolutePath().normalize());
        Path classes = location(MoudMod.class.getName());
        if (classes != null) paths.add(classes);
        return List.copyOf(paths);
    }

    private static @Nullable Path location(String className) {
        try {
            Class<?> type = Class.forName(className, false, GameExport.class.getClassLoader());
            CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (ClassNotFoundException | URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Path resourcesBeside(Path classes) {
        String text = classes.toString().replace(File.separatorChar, '/');
        if (!Files.isDirectory(classes) || !text.endsWith("/classes/java/main")) return null;
        Path resources = classes.getParent().getParent().getParent().resolve("resources").resolve("main");
        return Files.isDirectory(resources) ? resources : null;
    }

    private static boolean companionOrigin(Path location) {
        for (ModContainer mod : companions()) {
            if (mod.getOrigin().getPaths().stream().anyMatch(path -> path.toAbsolutePath().normalize().equals(location))) return true;
        }
        return false;
    }

    private static @Nullable Path projectRoot(List<Path> engine) {
        for (Path path : engine) {
            for (Path at = path.toAbsolutePath(); at != null; at = at.getParent()) {
                if (Files.isRegularFile(at.resolve("settings.gradle")) || Files.isRegularFile(at.resolve("settings.gradle.kts"))) return at;
            }
        }
        return null;
    }

    private static List<String> nestedFrom(List<Path> engine, boolean packaged) throws IOException {
        List<String> names = new ArrayList<>();
        if (!packaged) return names;
        try (ZipFile zip = new ZipFile(engine.getFirst().toFile())) {
            zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("META-INF/jars/") && name.endsWith(".jar") && !name.toLowerCase(Locale.ROOT).contains("imgui"))
                    .forEach(names::add);
        }
        return names;
    }

    private static List<ModContainer> companions() {
        List<ModContainer> found = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.equals("moud") || mod.getContainingMod().isPresent()) continue;
            boolean companion = COMPANION_MODS.contains(id) || mod.getMetadata().getDependencies().stream().anyMatch(d -> d.getModId().equals("moud"));
            if (companion) found.add(mod);
        }
        return found;
    }

    private static byte[] jarOf(List<Path> paths) throws IOException {
        if (paths.size() == 1 && Files.isRegularFile(paths.getFirst())) return Files.readAllBytes(paths.getFirst());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(bytes, manifest)) {
            Writer writer = new Writer(jar);
            for (Path path : paths) {
                if (!Files.isDirectory(path)) continue;
                try (Stream<Path> walk = Files.walk(path, DEPTH)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        String name = path.relativize(file).toString().replace(File.separatorChar, '/');
                        if (!name.equals("META-INF/MANIFEST.MF")) writer.put(name, Files.readAllBytes(file));
                    }
                }
            }
            writer.finish();
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static String modJson(List<Path> engine, PlaceConfig config, List<String> nested) throws IOException {
        byte[] original = null;
        for (Path source : engine) {
            if (Files.isDirectory(source) && Files.isRegularFile(source.resolve("fabric.mod.json"))) original = Files.readAllBytes(source.resolve("fabric.mod.json"));
            else if (Files.isRegularFile(source)) original = entry(source, "fabric.mod.json");
            if (original != null) break;
        }
        if (original == null) throw new IOException("the engine has no fabric.mod.json");
        if (!(Json.parse(new String(original, StandardCharsets.UTF_8)) instanceof Map<?, ?> parsed)) throw new IOException("the engine fabric.mod.json is not an object");
        Map<String, Object> mod = new LinkedHashMap<>((Map<String, Object>) parsed);
        mod.put("name", config.name());
        mod.put("version", config.version());
        mod.put("description", config.name() + ", made with Moud");
        List<Map<String, Object>> jars = new ArrayList<>();
        for (String name : nested) jars.add(Map.of("file", name));
        mod.put("jars", jars);
        Map<String, Object> custom = mod.get("custom") instanceof Map<?, ?> existing ? new LinkedHashMap<>((Map<String, Object>) existing) : new LinkedHashMap<>();
        custom.put("moud:game", config.id());
        mod.put("custom", custom);
        return Json.write(mod);
    }

    private static byte @Nullable [] entry(Path jar, String name) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals(name)) return in.readAllBytes();
            }
        }
        return null;
    }

    private static Manifest manifest(PlaceConfig config) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Multi-Release"), "true");
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, config.name());
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, config.version());
        return manifest;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IOException cancelled() {
        return new IOException("the export was cancelled");
    }

    private static JsonObject readTables() {
        try (InputStream in = GameExport.class.getResourceAsStream(TABLES)) {
            if (in == null) throw new IOException(TABLES + " is missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("the export tables could not be read", e);
            return new JsonObject();
        }
    }

    private static List<String> strings(String key) {
        if (!(TABLE.get(key) instanceof JsonArray array)) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) values.add(element.getAsString());
        return List.copyOf(values);
    }
}
