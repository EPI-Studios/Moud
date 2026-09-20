package com.meekdev.moud.mod.client.editor.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.core.place.TomlEdit;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Switches;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.project.ProjectIcons;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceToml;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class ProjectSettingsDialog {

    private static final String DIALOG = "##project-settings";
    private static final String AREAS_FILE = "/assets/moud/editor/project-settings.json";
    private static final float WIDTH = 640.0f;
    private static final float BODY_HEIGHT = 470.0f;
    private static final float LABEL_WIDTH = 130.0f;
    private static final float IMAGE_PREVIEW = 72.0f;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+([-+][A-Za-z0-9.-]+)?");

    private record Area(String title, List<Feature> features) {}

    private static final List<Area> AREAS = new ArrayList<>();
    private static final Map<Feature, String> ABOUT = new EnumMap<>(Feature.class);

    static {
        loadAreas();
    }

    private final SceneDocument document;
    private final FileBrowser browser;
    private final ProjectIcons images = new ProjectIcons();
    private final ImString name = new ImString(128);
    private final ImString id = new ImString(64);
    private final ImString version = new ImString(32);
    private final ImString engine = new ImString(32);
    private final ImString server = new ImString(256);
    private final ImString client = new ImString(256);
    private final ImString scene = new ImString(256);
    private final ImInt maxPlayers = new ImInt(16);
    private final ImString featureSearch = new ImString(64);
    private final Map<Feature, Boolean> overrides = new LinkedHashMap<>();
    private final List<String> writtenFeatureKeys = new ArrayList<>();
    private final Features defaults = PlaceToml.defaultFeatures();
    private PlaceConfig loaded = PlaceConfig.DEFAULT;
    private List<String> scripts = List.of();
    private List<String> scenes = List.of();
    private String problem = "";
    private boolean open;
    private boolean needsReopen;

    ProjectSettingsDialog(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.browser = new FileBrowser(icons);
    }

    void open() {
        Path root = ClientPlace.root();
        if (root == null) return;
        loaded = PlaceConfig.DEFAULT;
        problem = "";
        try {
            Path toml = root.resolve("place.toml");
            if (Files.isRegularFile(toml)) loaded = PlaceConfig.parse(Files.readString(toml));
        } catch (IOException | IllegalArgumentException e) {
            problem = "place.toml could not be read: " + e.getMessage() + ". Saving here rewrites the settings shown.";
        }
        name.set(loaded.name());
        id.set(loaded.id());
        version.set(loaded.version());
        engine.set(loaded.engine());
        server.set(loaded.server());
        client.set(loaded.client());
        scene.set(loaded.scene());
        maxPlayers.set(loaded.maxPlayers());
        overrides.clear();
        writtenFeatureKeys.clear();
        for (Map.Entry<String, Boolean> one : loaded.features().entrySet()) {
            writtenFeatureKeys.add(one.getKey());
            for (Feature feature : Feature.values()) {
                if (feature.key().equalsIgnoreCase(one.getKey())) overrides.put(feature, one.getValue());
            }
        }
        scripts = list(root, "script");
        scenes = list(root, "scene");
        needsReopen = false;
        open = true;
    }

    void render() {
        if (open) {
            ImGui.openPopup(DIALOG);
            open = false;
        }
        if (!Dialogs.begin(DIALOG, WIDTH)) return;
        Dialogs.title("Project settings");
        Texts.muted("Stored in place.toml at the root of the place");
        if (!problem.isEmpty()) Texts.colored(EditorStyle.COLOR_WARNING, problem);
        Dialogs.gap();
        ImGui.beginChild("##settings-body", 0, EditorScale.of(BODY_HEIGHT), false);
        if (Sections.header("General")) renderGeneral();
        if (Sections.header("Entry")) renderEntry();
        if (Sections.header("Features")) renderFeatures();
        ImGui.endChild();
        String invalid = validate();
        Dialogs.gap();
        if (invalid != null) Texts.colored(EditorStyle.COLOR_DANGER, invalid);
        else if (needsReopen) Texts.muted("Saved. Entry scripts and the start scene apply when the place reopens.");
        else Texts.muted("Features apply as soon as you save.");
        Dialogs.alignFooter(needsReopen ? 3 : 2);
        if (Dialogs.button("Close##settings-close") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        if (needsReopen) {
            ImGui.sameLine();
            if (Dialogs.button("Reopen##settings-reopen")) {
                ImGui.closeCurrentPopup();
                Path root = ClientPlace.root();
                if (root != null && !document.dirty()) Editor.requestReopenProject(root);
                else Output.add(Output.Level.WARN, "editor", "Save the scene before reopening the place");
            }
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Save##settings-save", invalid == null)) save();
        browser.render();
        Dialogs.end();
    }

    private void renderGeneral() {
        field("Name", "The title players see", () -> input("##name", name, "My place"));
        field("Id", "Lowercase letters, digits and dashes. It names saves and exports.", () -> input("##id", id, "my-place"));
        field("Version", "major.minor.patch", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - ImGui.getFrameHeight() * 3 - ImGui.getStyle().getItemSpacingX());
            ImGui.inputTextWithHint("##version", "0.1.0", version);
            ImGui.sameLine();
            if (ImGui.button("Bump", ImGui.getFrameHeight() * 3, 0)) version.set(bump(version.get()));
            if (ImGui.isItemHovered()) ImGui.setTooltip("Raise the last number");
        });
        field("Max players", "How many can join at once", () -> {
            ImGui.setNextItemWidth(EditorScale.of(120));
            if (ImGui.inputInt("##players", maxPlayers)) maxPlayers.set(Math.max(1, Math.min(1000, maxPlayers.get())));
        });
        field("Engine", "The Moud version this place was made for, empty for any", () -> input("##engine", engine, "any"));
        field("Image", "A PNG shown on the project card in the hub and used as the window icon", this::renderImage);
    }

    private void renderImage() {
        Path root = ClientPlace.root();
        float size = EditorScale.of(IMAGE_PREVIEW);
        Optional<ProjectIcons.Image> texture = root == null ? Optional.empty() : images.of(root);
        if (texture.isPresent()) {
            float aspect = texture.get().aspect();
            float sideways = Math.max(0.0f, (1.0f - 1.0f / aspect) * 0.5f);
            float crop = Math.max(0.0f, (1.0f - aspect) * 0.5f);
            ImGui.image(texture.get().textureId(), size, size, sideways, crop, 1.0f - sideways, 1.0f - crop);
        } else {
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            ImGui.dummy(size, size);
            ImGui.getWindowDrawList().addRect(x, y, x + size, y + size, EditorStyle.COLOR_OUTLINE, EditorStyle.frameRounding());
        }
        ImGui.sameLine();
        ImGui.beginGroup();
        if (ImGui.button(texture.isPresent() ? "Replace##image" : "Choose a PNG##image")) chooseImage(root);
        if (texture.isPresent()) {
            ImGui.sameLine();
            if (ImGui.button("Remove##image")) removeImage(root);
        }
        Texts.muted(texture.isPresent() ? imageFile(root) : "No image yet, the card draws a cube instead");
        ImGui.endGroup();
    }

    private void chooseImage(@Nullable Path root) {
        if (root == null) return;
        browser.chooseFile("Choose the project image", root, IMAGE_EXTENSIONS, file -> {
            try {
                Files.copy(file, root.resolve(ProjectIcons.CANDIDATE_FILENAMES.getFirst()), StandardCopyOption.REPLACE_EXISTING);
                images.forget(root);
                Output.add(Output.Level.INFO, "editor", "The project image is now " + file.getFileName());
            } catch (IOException e) {
                problem = "The image could not be copied: " + e.getMessage();
            }
        });
    }

    private void removeImage(@Nullable Path root) {
        if (root == null) return;
        try {
            for (String candidate : ProjectIcons.CANDIDATE_FILENAMES) Files.deleteIfExists(root.resolve(candidate));
            images.forget(root);
        } catch (IOException e) {
            problem = "The image could not be removed: " + e.getMessage();
        }
    }

    private static String imageFile(@Nullable Path root) {
        if (root == null) return "";
        for (String candidate : ProjectIcons.CANDIDATE_FILENAMES) {
            if (Files.isRegularFile(root.resolve(candidate))) return candidate;
        }
        return "";
    }

    private void renderEntry() {
        field("Server script", "Runs on the server when the place starts", () -> pick("##server", server, scripts, "server/"));
        field("Client script", "Runs on every player's game", () -> pick("##client", client, scripts, "client/"));
        field("Start scene", "Loaded under game.world before the server script runs", () -> pick("##scene", scene, scenes, ""));
    }

    private void renderFeatures() {
        SearchField.render("##feature-search", "Search features", featureSearch, EditorScale.of(220));
        ImGui.sameLine();
        long changed = overrides.entrySet().stream().filter(e -> e.getValue() != defaults.isOn(e.getKey())).count();
        Texts.muted(changed == 0 ? "All at their defaults" : changed + " changed from the defaults");
        ImGui.sameLine();
        if (!overrides.isEmpty() && ImGui.smallButton("Reset all")) overrides.clear();
        String needle = featureSearch.get().strip().toLowerCase(Locale.ROOT);
        for (Area area : AREAS) {
            List<Feature> shown = area.features().stream().filter(f -> needle.isEmpty() || f.key().toLowerCase(Locale.ROOT).contains(needle)
                    || ABOUT.getOrDefault(f, "").toLowerCase(Locale.ROOT).contains(needle)).toList();
            if (shown.isEmpty()) continue;
            Dialogs.gap();
            Texts.colored(EditorStyle.COLOR_ACCENT, area.title());
            for (Feature feature : shown) renderFeature(feature);
        }
    }

    private void renderFeature(Feature feature) {
        ImGui.pushID(feature.key());
        boolean fallback = defaults.isOn(feature);
        Boolean set = overrides.get(feature);
        boolean value = set == null ? fallback : set;
        ImGui.alignTextToFramePadding();
        if (Switches.draw("##switch", value) != value) overrides.put(feature, !value);
        if (ImGui.beginPopupContextItem("##feature-menu")) {
            if (ImGui.menuItem("Reset to default (" + (fallback ? "on" : "off") + ")")) overrides.remove(feature);
            ImGui.endPopup();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Right click to reset");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(feature.key());
        if (set != null && set != fallback) {
            ImGui.sameLine();
            Texts.colored(EditorStyle.COLOR_WARNING, "changed");
        }
        ImGui.sameLine(EditorScale.of(250));
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(ABOUT.getOrDefault(feature, ""));
        ImGui.popStyleColor();
        ImGui.popID();
    }

    private void save() {
        Path root = ClientPlace.root();
        if (root == null) return;
        Path toml = root.resolve("place.toml");
        try {
            String text = Files.isRegularFile(toml) ? Files.readString(toml) : "";
            text = TomlEdit.set(text, "", "name", name.get().strip());
            text = TomlEdit.set(text, "", "id", id.get().strip());
            text = TomlEdit.set(text, "", "version", version.get().strip());
            text = TomlEdit.set(text, "", "maxPlayers", maxPlayers.get());
            text = engine.get().isBlank() ? TomlEdit.remove(text, "", "engine") : TomlEdit.set(text, "", "engine", engine.get().strip());
            text = entry(text, "server", server.get(), PlaceConfig.DEFAULT.server());
            text = entry(text, "client", client.get(), PlaceConfig.DEFAULT.client());
            text = entry(text, "scene", scene.get(), "");
            for (String key : writtenFeatureKeys) text = TomlEdit.remove(text, "features", key);
            writtenFeatureKeys.clear();
            for (Map.Entry<Feature, Boolean> one : overrides.entrySet()) {
                text = TomlEdit.set(text, "features", one.getKey().key(), one.getValue());
                writtenFeatureKeys.add(one.getKey().key());
            }
            PlaceConfig parsed = PlaceConfig.parse(text);
            Files.writeString(toml, text);
            needsReopen = !parsed.server().equals(loaded.server()) || !parsed.client().equals(loaded.client()) || !parsed.scene().equals(loaded.scene())
                    || parsed.maxPlayers() != loaded.maxPlayers();
            loaded = parsed;
            problem = "";
            PlaceToml.reload(MoudMod.features());
            Output.add(Output.Level.INFO, "editor", "Saved place.toml");
        } catch (IOException | IllegalArgumentException e) {
            problem = "Not saved: " + e.getMessage();
        }
    }

    private static String entry(String text, String key, String value, String fallback) {
        String clean = value.strip();
        return clean.isEmpty() || clean.equals(fallback) ? TomlEdit.remove(text, "entry", key) : TomlEdit.set(text, "entry", key, clean);
    }

    private @Nullable String validate() {
        if (name.get().isBlank()) return "The place needs a name";
        if (!ID.matcher(id.get().strip()).matches()) return "The id may only hold lowercase letters, digits and dashes";
        if (!VERSION.matcher(version.get().strip()).matches()) return "The version looks like 1.2.3";
        for (ImString path : List.of(server, client)) {
            String value = path.get().strip();
            if (!value.isEmpty() && !value.startsWith("res://")) return value + " should start with res://";
        }
        String start = scene.get().strip();
        if (!start.isEmpty() && !start.endsWith(".scene")) return "The start scene should be a .scene file";
        return null;
    }

    private static void field(String label, String help, Runnable editor) {
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        if (ImGui.isItemHovered()) ImGui.setTooltip(help);
        ImGui.sameLine(EditorScale.of(LABEL_WIDTH));
        ImGui.pushID(label);
        editor.run();
        ImGui.popID();
    }

    private static void input(String id, ImString value, String hint) {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint(id, hint, value);
    }

    private static void pick(String id, ImString value, List<String> choices, String prefix) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - ImGui.getFrameHeight() - ImGui.getStyle().getItemSpacingX());
        ImGui.inputTextWithHint(id, "res://...", value);
        ImGui.sameLine();
        if (ImGui.arrowButton(id + "-pick", 3)) ImGui.openPopup(id + "-choices");
        if (ImGui.beginPopup(id + "-choices")) {
            int shown = 0;
            for (String choice : choices) {
                if (!choice.startsWith("res://" + prefix)) continue;
                shown++;
                if (ImGui.selectable(choice, choice.equals(value.get()))) value.set(choice);
            }
            if (shown == 0) Texts.muted("Nothing found under " + (prefix.isEmpty() ? "the place" : prefix));
            ImGui.endPopup();
        }
    }

    private static List<String> list(Path root, String kind) {
        List<String> extensions = Languages.extensions();
        try (Stream<Path> walk = Files.walk(root, 8)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> !root.relativize(file).toString().startsWith("."))
                    .filter(file -> {
                        String fileName = file.getFileName().toString();
                        if (kind.equals("scene")) return fileName.endsWith(".scene");
                        return extensions.stream().anyMatch(extension -> fileName.endsWith("." + extension) && !fileName.endsWith(".d." + extension));
                    })
                    .map(AssetFiles::res)
                    .filter(res -> res != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String bump(String current) {
        String[] parts = current.strip().split("[.-]");
        if (parts.length < 3) return "0.1.0";
        try {
            return parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1);
        } catch (NumberFormatException e) {
            return current;
        }
    }

    private static void loadAreas() {
        try (InputStream in = ProjectSettingsDialog.class.getResourceAsStream(AREAS_FILE)) {
            if (in == null) throw new IOException(AREAS_FILE + " is missing");
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject area = element.getAsJsonObject();
                List<Feature> features = new ArrayList<>();
                for (Map.Entry<String, JsonElement> entry : area.getAsJsonObject("features").entrySet()) {
                    Feature feature = Feature.valueOf(entry.getKey());
                    features.add(feature);
                    ABOUT.put(feature, entry.getValue().getAsString());
                }
                AREAS.add(new Area(area.get("title").getAsString(), features));
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("project settings areas could not be read", e);
        }
    }
}
