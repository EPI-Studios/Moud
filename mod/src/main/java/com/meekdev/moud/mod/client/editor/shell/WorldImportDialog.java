package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.editor.document.SceneLink;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.level.AnvilWorlds;
import com.meekdev.moud.mod.place.Blocks;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.SceneBackups;
import com.meekdev.moud.mod.server.VoidLevel;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.hollowcube.polar.PolarWorld;
import net.hollowcube.polar.PolarWriter;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

final class WorldImportDialog {

    private static final String DIALOG = "##world-import";
    private static final float WIDTH = 560.0f;
    private static final float SAVES_HEIGHT = 130.0f;
    private static final int LARGE = 4000;
    private static final int DEFAULT_RADIUS = 24;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private enum Stage { PICK, RUNNING, DONE, FAILED }

    private final ImString folder = new ImString(1024);
    private final ImInt dimension = new ImInt();
    private final ImInt radius = new ImInt(DEFAULT_RADIUS);
    private final int[] centre = new int[2];
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final AtomicReference<AnvilWorlds.Progress> progress = new AtomicReference<>();
    private final AtomicReference<String> outcome = new AtomicReference<>("");
    private volatile Stage stage = Stage.PICK;
    private boolean open;
    private String inspected = "";
    private List<AnvilWorlds.Dimension> dimensions = List.of();
    private List<Path> saves = List.of();
    private int estimate = -1;
    private boolean everything;
    private volatile boolean loadWhenDone;
    private String scene = "";

    WorldImportDialog() {}

    void open() {
        if (stage == Stage.RUNNING) {
            open = true;
            return;
        }
        stage = Stage.PICK;
        saves = findSaves();
        open = true;
    }

    void render() {
        if (loadWhenDone) {
            loadWhenDone = false;
            if (scene.equals(SceneLink.file())) SceneLink.reloadTerrain();
        }
        if (open) {
            ImGui.openPopup(DIALOG);
            open = false;
        }
        if (!Dialogs.begin(DIALOG, WIDTH)) return;
        switch (stage) {
            case PICK -> renderPick();
            case RUNNING -> renderRunning();
            case DONE -> renderDone();
            case FAILED -> renderFailed();
        }
        Dialogs.end();
    }

    private void renderPick() {
        Dialogs.title("Import a Minecraft world");
        String open = SceneLink.file();
        Texts.wrapped("The blocks of a Java Edition world become the terrain of " + name(open) + ", saved next to it as " + name(open) + ".polar. A scene has one terrain, so importing replaces the one it has, and other scenes keep theirs. Instances and scripts stay as they are.");
        Dialogs.gap();
        if (!saves.isEmpty()) {
            Texts.muted("Worlds on this computer");
            ImGui.beginChild("##world-saves", 0, EditorScale.of(SAVES_HEIGHT), true);
            for (Path save : saves) {
                if (ImGui.selectable(save.getFileName() + "##" + save, save.toString().equals(folder.get()))) folder.set(save.toString());
                if (ImGui.isItemHovered()) ImGui.setTooltip(save.toString());
            }
            ImGui.endChild();
        }
        Texts.muted("World folder, the one holding level.dat");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - Dialogs.buttonWidth() - ImGui.getStyle().getItemSpacingX());
        ImGui.inputTextWithHint("##world-folder", "/path/to/.minecraft/saves/My World", folder);
        ImGui.sameLine();
        if (ImGui.button("Browse...", Dialogs.buttonWidth(), 0)) browse();
        inspect();
        Path world = Path.of(folder.get().strip().isEmpty() ? "." : folder.get().strip());
        boolean valid = !folder.get().isBlank() && !dimensions.isEmpty();
        if (!folder.get().isBlank() && dimensions.isEmpty()) {
            Texts.colored(EditorStyle.COLOR_WARNING, Files.isDirectory(world) ? "No region files here. Pick the world folder itself, not saves/ or region/." : "That folder does not exist.");
        }
        if (valid) {
            Dialogs.gap();
            String[] labels = dimensions.stream().map(d -> d.label() + "  (" + d.regionFiles() + " region files)").toArray(String[]::new);
            ImGui.setNextItemWidth(-1);
            if (ImGui.combo("##world-dimension", dimension, labels)) estimate = -1;
            if (ImGui.checkbox("Everything", everything)) {
                everything = !everything;
                estimate = -1;
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Import every generated chunk. Large worlds take a lot of memory and disk.");
            if (!everything) {
                ImGui.sameLine();
                ImGui.setNextItemWidth(EditorScale.of(120));
                if (ImGui.inputInt("chunks around##world-radius", radius)) {
                    radius.set(Math.max(1, Math.min(512, radius.get())));
                    estimate = -1;
                }
                ImGui.sameLine();
                ImGui.setNextItemWidth(EditorScale.of(150));
                if (ImGui.inputInt2("chunk X Z##world-centre", centre)) estimate = -1;
                if (ImGui.isItemHovered()) ImGui.setTooltip("Starts at the world spawn. One chunk is 16 blocks.");
            }
            AnvilWorlds.Plan plan = plan();
            if (estimate < 0) estimate = AnvilWorlds.count(plan);
            Texts.muted(estimate + " chunks, " + span(plan));
            if (estimate > LARGE) Texts.colored(EditorStyle.COLOR_WARNING, "That is a lot of terrain. The whole place loads it into memory, a smaller area starts faster.");
            Path root = ClientPlace.root();
            if (root != null && !open.isEmpty() && Blocks.terrainOf(root, open) != null) {
                Texts.colored(EditorStyle.COLOR_WARNING, name(open) + " already has terrain. It is moved into .moud/backups before being replaced.");
            }
        }
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##world-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.primaryButton("Import##world-go", valid && estimate > 0)) start(plan());
    }

    private void renderRunning() {
        AnvilWorlds.Progress now = progress.get();
        Dialogs.title("Importing the world");
        float fraction = now == null || now.regions() == 0 ? 0 : (float) now.regionsDone() / now.regions();
        ImGui.progressBar(fraction, -1, 0, now == null ? "reading regions" : now.regionsDone() + " / " + now.regions() + " regions");
        Texts.muted(now == null ? "Starting" : now.chunks() + " chunks converted" + (now.skipped() > 0 ? ", " + now.skipped() + " unfinished or unreadable left out" : ""));
        if (now != null && now.regionsDone() == now.regions()) Texts.muted("Compressing and writing " + name(scene) + ".polar");
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Hide##world-hide")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.button("Stop##world-stop")) cancel.set(true);
    }

    private void renderDone() {
        Dialogs.title("The world is imported");
        Texts.wrapped(outcome.get());
        Texts.wrapped(scene.equals(SceneLink.file()) ? "It is loaded around you now." : "It loads when " + name(scene) + " is opened.");
        Dialogs.gap();
        Dialogs.alignFooter(1);
        if (Dialogs.primaryButton("Done##world-done", true) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            stage = Stage.PICK;
            ImGui.closeCurrentPopup();
        }
    }

    private void renderFailed() {
        Dialogs.title("The import stopped");
        Texts.colored(EditorStyle.COLOR_DANGER, outcome.get());
        Texts.muted("Nothing was replaced, the scene keeps the terrain it had.");
        Dialogs.gap();
        Dialogs.alignFooter(1);
        if (Dialogs.button("Back##world-back")) stage = Stage.PICK;
    }

    private void start(AnvilWorlds.Plan plan) {
        Path root = ClientPlace.root();
        if (root == null || SceneLink.file().isEmpty()) return;
        scene = SceneLink.file();
        cancel.set(false);
        progress.set(null);
        stage = Stage.RUNNING;
        Path source = Path.of(folder.get().strip());
        String into = scene;
        Thread worker = new Thread(() -> run(root, into, source, plan), "moud world import");
        worker.setDaemon(true);
        worker.start();
    }

    private void run(Path root, String into, Path source, AnvilWorlds.Plan plan) {
        long began = System.currentTimeMillis();
        try {
            PolarWorld polar = AnvilWorlds.convert(plan, progress::set, cancel);
            if (polar.chunks().isEmpty()) throw new IOException("no finished chunks in that area, move the centre or import everything");
            byte[] bytes = PolarWriter.write(polar);
            if (cancel.get()) throw new AnvilWorlds.Cancelled();
            Path target = Blocks.terrainFile(root, into);
            Path temporary = target.resolveSibling(target.getFileName() + ".importing");
            Files.createDirectories(target.getParent());
            Files.write(temporary, bytes);
            Path existing = Blocks.terrainOf(root, into);
            if (existing != null) {
                Path backups = SceneBackups.folder(root);
                Files.createDirectories(backups);
                Files.move(existing, backups.resolve(name(into) + "-" + LocalDateTime.now().format(STAMP) + ".polar"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            String summary = String.format(Locale.ROOT, "%d chunks of %s from %s, %.1f MB, in %.1f s.",
                    polar.chunks().size(), plan.dimension().label(), source.getFileName(), bytes.length / 1_048_576.0,
                    (System.currentTimeMillis() - began) / 1000.0);
            outcome.set(summary);
            Output.add(Output.Level.INFO, "editor", "Imported " + summary + " as the terrain of " + into);
            stage = Stage.DONE;
            loadWhenDone = true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            MoudMod.LOG.warn("world import failed", e);
            outcome.set(e instanceof OutOfMemoryError ? "Ran out of memory, import a smaller area." : String.valueOf(e.getMessage()));
            try {
                Path target = Blocks.terrainFile(root, into);
                Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".importing"));
            } catch (IOException | RuntimeException ignored) {
            }
            stage = Stage.FAILED;
        }
    }

    private static String name(String scene) {
        String file = scene.substring(scene.lastIndexOf('/') + 1);
        return file.endsWith(".scene") ? file.substring(0, file.length() - ".scene".length()) : file;
    }

    private void inspect() {
        String now = folder.get().strip();
        if (now.equals(inspected)) return;
        inspected = now;
        estimate = -1;
        dimension.set(0);
        if (now.isEmpty()) {
            dimensions = List.of();
            return;
        }
        Path world = Path.of(now);
        dimensions = AnvilWorlds.dimensions(world);
        int[] spawn = AnvilWorlds.spawnChunk(world);
        centre[0] = spawn[0];
        centre[1] = spawn[1];
    }

    private AnvilWorlds.Plan plan() {
        AnvilWorlds.Dimension chosen = dimensions.get(Math.min(dimension.get(), dimensions.size() - 1));
        return new AnvilWorlds.Plan(chosen, centre[0], centre[1], everything ? 0 : radius.get());
    }

    private static String span(AnvilWorlds.Plan plan) {
        if (plan.radius() <= 0) return "everything generated";
        int blocks = (plan.radius() * 2 + 1) * 16;
        return blocks + " × " + blocks + " blocks around chunk " + plan.centreX() + ", " + plan.centreZ();
    }

    private void browse() {
        String start = folder.get().isBlank() ? System.getProperty("user.home") : folder.get();
        String picked = TinyFileDialogs.tinyfd_selectFolderDialog("Pick a Minecraft world folder", start);
        if (picked != null) folder.set(picked);
    }

    private static List<Path> findSaves() {
        List<Path> roots = new ArrayList<>();
        roots.add(Minecraft.getInstance().gameDirectory.toPath().resolve("saves"));
        roots.add(Path.of(System.getProperty("user.home"), ".minecraft", "saves"));
        String appData = System.getenv("APPDATA");
        if (appData != null) roots.add(Path.of(appData, ".minecraft", "saves"));
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> worlds = Files.list(root)) {
                worlds.filter(Files::isDirectory)
                        .filter(world -> Files.isRegularFile(world.resolve("level.dat")))
                        .filter(world -> !world.getFileName().toString().equals(VoidLevel.NAME))
                        .filter(world -> !found.contains(world.toAbsolutePath().normalize()))
                        .map(world -> world.toAbsolutePath().normalize())
                        .sorted()
                        .forEach(found::add);
            } catch (IOException ignored) {
            }
        }
        return found;
    }

    boolean running() {
        return stage == Stage.RUNNING;
    }

    @Nullable String status() {
        AnvilWorlds.Progress now = progress.get();
        if (stage != Stage.RUNNING || now == null) return stage == Stage.RUNNING ? "Importing world" : null;
        return "Importing world " + now.regionsDone() + "/" + now.regions();
    }
}
