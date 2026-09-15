package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.place.Output;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

final class MinecraftBrowser {

    enum Section { TEXTURES, SOUNDS }

    record Group(Section section, String key, String label) {}

    record Item(Group group, String id, String name, String haystack) {}

    private static final float CELL_SIZE = 64.0f;
    private static final float CELL_PADDING = 10.0f;
    private static final float LABEL_HEIGHT = 20.0f;
    private static final float ROW_HEIGHT = 22.0f;
    private static final float TOOLTIP_PREVIEW = 128.0f;
    private static final Map<String, String> TEXTURE_LABELS = Map.of(
            "block", "Blocks", "item", "Items", "entity", "Entities", "particle", "Particles", "gui", "Interface",
            "painting", "Paintings", "mob_effect", "Effects", "environment", "Sky and weather", "models", "Armour and models");

    private final Thumbnails thumbnails;
    private final IconWidgets icons;
    private final Consumer<String> place;
    private final Supplier<Path> folder;
    private final ImGuiListClipper clipper = new ImGuiListClipper();
    private final Map<Group, List<Item>> index = new LinkedHashMap<>();
    private @Nullable Group open;
    private @Nullable String selected;
    private boolean built;

    MinecraftBrowser(Thumbnails thumbnails, IconWidgets icons, Consumer<String> place, Supplier<Path> folder) {
        this.thumbnails = thumbnails;
        this.icons = icons;
        this.place = place;
        this.folder = folder;
    }

    boolean active() {
        return open != null;
    }

    void leave() {
        open = null;
    }

    String location() {
        return open == null ? "" : "Minecraft / " + (open.section() == Section.TEXTURES ? "Textures" : "Sounds") + " / " + open.label();
    }

    void renderTree() {
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (!ImGui.treeNodeEx("##minecraft", flags, "Minecraft")) return;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Everything the game ships with, and the loaded resource packs");
        ensureIndex();
        for (Section section : Section.values()) {
            String title = section == Section.TEXTURES ? "Textures" : "Sounds";
            if (!ImGui.treeNodeEx("##minecraft-" + section, ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth, title)) continue;
            for (Map.Entry<Group, List<Item>> entry : index.entrySet()) {
                Group group = entry.getKey();
                if (group.section() != section) continue;
                int leaf = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
                if (group.equals(open)) leaf |= ImGuiTreeNodeFlags.Selected;
                ImGui.treeNodeEx("##mc-" + section + group.key(), leaf, group.label() + "  " + entry.getValue().size());
                if (ImGui.isItemClicked()) {
                    open = group;
                    selected = null;
                }
            }
            ImGui.treePop();
        }
        ImGui.treePop();
    }

    void renderEntries(String filter, boolean grid) {
        if (open == null) return;
        ensureIndex();
        List<Item> items = visible(filter);
        if (ImGui.button("Reload##mc-reload")) rebuild();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Read the resources again, after changing resource packs");
        ImGui.sameLine();
        Texts.muted(filter.isBlank() ? items.size() + " items" : items.size() + " matches in every " + (open.section() == Section.TEXTURES ? "texture" : "sound"));
        ImGui.separator();
        if (items.isEmpty()) {
            EmptyStates.centered("Nothing matches", List.of("Search looks through every group of this section"));
            return;
        }
        if (open.section() == Section.SOUNDS || !grid) renderRows(items);
        else renderGrid(items);
    }

    private List<Item> visible(String filter) {
        if (open == null) return List.of();
        String needle = filter.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return index.getOrDefault(open, List.of());
        List<Item> found = new ArrayList<>();
        for (Map.Entry<Group, List<Item>> entry : index.entrySet()) {
            if (entry.getKey().section() != open.section()) continue;
            for (Item item : entry.getValue()) {
                if (item.haystack().contains(needle)) found.add(item);
            }
        }
        return found;
    }

    private void renderGrid(List<Item> items) {
        float cell = EditorScale.of(CELL_SIZE);
        float label = EditorScale.of(LABEL_HEIGHT);
        float cellWidth = cell + EditorScale.of(CELL_PADDING);
        int columns = Math.max(1, (int) (ImGui.getContentRegionAvailX() / cellWidth));
        int rows = (items.size() + columns - 1) / columns;
        clipper.begin(rows, cell + label + ImGui.getStyle().getItemSpacingY());
        while (clipper.step()) {
            for (int row = clipper.getDisplayStart(); row < clipper.getDisplayEnd(); row++) {
                for (int column = 0; column < columns; column++) {
                    int at = row * columns + column;
                    if (at >= items.size()) break;
                    if (column > 0) ImGui.sameLine();
                    renderCell(items.get(at), cell, label);
                }
            }
        }
        clipper.end();
    }

    private void renderCell(Item item, float cell, float label) {
        ImGui.pushID(item.id());
        ImGui.beginGroup();
        float x = ImGui.getCursorPosX();
        float y = ImGui.getCursorPosY();
        if (ImGui.selectable("##cell", item.id().equals(selected), ImGuiSelectableFlags.AllowDoubleClick, cell, cell + label)) {
            selected = item.id();
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) activate(item);
        }
        interact(item);
        ImGui.setCursorPos(x, y);
        OptionalLong texture = texture(item);
        if (texture.isPresent()) ImGui.image(texture.getAsLong(), cell, cell);
        else icons.draw(EditorIcon.TEXTURE_2D, cell);
        ImGui.setCursorPos(x, y + cell);
        String text = fit(item.name(), cell);
        float indent = (cell - ImGui.calcTextSize(text).x) * 0.5f;
        if (indent > 0) ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        ImGui.textUnformatted(text);
        ImGui.setCursorPos(x, y + cell + label);
        ImGui.endGroup();
        ImGui.popID();
    }

    private void renderRows(List<Item> items) {
        float height = EditorScale.of(ROW_HEIGHT);
        clipper.begin(items.size(), height + ImGui.getStyle().getItemSpacingY());
        while (clipper.step()) {
            for (int at = clipper.getDisplayStart(); at < clipper.getDisplayEnd(); at++) renderRow(items.get(at), height);
        }
        clipper.end();
    }

    private void renderRow(Item item, float height) {
        ImGui.pushID(item.id());
        float y = ImGui.getCursorPosY();
        float x = ImGui.getCursorPosX();
        if (ImGui.selectable("##row", item.id().equals(selected), ImGuiSelectableFlags.AllowDoubleClick | ImGuiSelectableFlags.AllowOverlap, 0, height)) {
            selected = item.id();
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) activate(item);
        }
        interact(item);
        float icon = EditorStyle.iconSizeSmall();
        ImGui.setCursorPos(x + EditorScale.of(4), y + (height - icon) * 0.5f - EditorStyle.framePaddingY());
        if (item.group().section() == Section.SOUNDS) {
            boolean playing = SoundPreview.playing(item.id());
            if (icons.iconButton("preview", playing ? EditorIcon.STOP : EditorIcon.PLAY, icon)) SoundPreview.toggle(item.id());
            if (ImGui.isItemHovered()) ImGui.setTooltip(playing ? "Stop" : "Listen");
        } else {
            OptionalLong texture = texture(item);
            if (texture.isPresent()) ImGui.image(texture.getAsLong(), icon, icon);
            else icons.draw(EditorIcon.TEXTURE_2D, icon);
        }
        ImGui.sameLine();
        ImGui.setCursorPosY(y + (height - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.textUnformatted(item.name());
        ImGui.sameLine();
        ImGui.setCursorPosY(y + (height - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(item.id());
        ImGui.popStyleColor();
        ImGui.setCursorPos(x, y);
        ImGui.dummy(1, height);
        ImGui.popID();
    }

    private void interact(Item item) {
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(AssetsPanel.PAYLOAD, item.id(), ImGuiCond.Once);
            ImGui.textUnformatted(item.id());
            ImGui.endDragDropSource();
        }
        if (ImGui.isItemHovered() && !ImGui.isMouseDragging(ImGuiMouseButton.Left)) tooltip(item);
        if (!ImGui.beginPopupContextItem("##mc-context")) return;
        selected = item.id();
        Texts.muted(item.id());
        ImGui.separator();
        if (ImGui.menuItem("Copy id")) ImGui.setClipboardText(item.id());
        if (item.group().section() == Section.SOUNDS) {
            if (ImGui.menuItem(SoundPreview.playing(item.id()) ? "Stop" : "Listen")) SoundPreview.toggle(item.id());
            if (ImGui.menuItem("Place a Sound in the scene")) place.accept(item.id());
            if (ImGui.menuItem("Copy as script")) ImGui.setClipboardText("sound.SoundId = \"" + item.id() + "\"");
        } else {
            if (ImGui.menuItem("Copy into the place")) copyIntoPlace(item);
        }
        ImGui.endPopup();
    }

    private void tooltip(Item item) {
        ImGui.beginTooltip();
        if (item.group().section() == Section.TEXTURES) {
            OptionalLong texture = texture(item);
            if (texture.isPresent()) ImGui.image(texture.getAsLong(), EditorScale.of(TOOLTIP_PREVIEW), EditorScale.of(TOOLTIP_PREVIEW));
        }
        ImGui.textUnformatted(item.name());
        Texts.muted(item.id());
        Texts.muted(item.group().section() == Section.SOUNDS ? "Double click to place, drag onto a SoundId" : "Drag onto an image field, right click to copy it into the place");
        ImGui.endTooltip();
    }

    private void activate(Item item) {
        if (item.group().section() == Section.SOUNDS) {
            place.accept(item.id());
            return;
        }
        ImGui.setClipboardText(item.id());
        Output.add(Output.Level.INFO, "editor", "Copied " + item.id());
    }

    private OptionalLong texture(Item item) {
        Identifier id = Identifier.tryParse(item.id());
        if (id == null) return OptionalLong.empty();
        return thumbnails.get(() -> open(id), "minecraft " + item.id(), true);
    }

    private static InputStream open(Identifier id) throws IOException {
        return Minecraft.getInstance().getResourceManager().open(id);
    }

    private void copyIntoPlace(Item item) {
        Identifier id = Identifier.tryParse(item.id());
        if (id == null) return;
        try (InputStream in = open(id)) {
            Path file = AssetFiles.unique(folder.get(), item.name().replace('/', '_') + ".png");
            Files.copy(in, file);
            Output.add(Output.Level.INFO, "editor", "Copied " + item.id() + " to " + AssetFiles.res(file));
        } catch (IOException e) {
            Output.add(Output.Level.WARN, "editor", "Could not copy " + item.id() + ": " + e.getMessage());
        }
    }

    private void ensureIndex() {
        if (!built) rebuild();
    }

    private void rebuild() {
        built = true;
        index.clear();
        Minecraft client = Minecraft.getInstance();
        Map<String, List<Item>> textures = new TreeMap<>();
        Map<String, Group> textureGroups = new LinkedHashMap<>();
        Map<Identifier, Resource> found = client.getResourceManager().listResources("textures", id -> id.getPath().endsWith(".png"));
        for (Identifier id : found.keySet()) {
            String path = id.getPath().substring("textures/".length(), id.getPath().length() - ".png".length());
            int slash = path.indexOf('/');
            String key = slash < 0 ? "misc" : path.substring(0, slash);
            String name = slash < 0 ? path : path.substring(slash + 1);
            Group group = textureGroups.computeIfAbsent(key, k -> new Group(Section.TEXTURES, k, TEXTURE_LABELS.getOrDefault(k, pretty(k))));
            String full = id.toString();
            textures.computeIfAbsent(key, k -> new ArrayList<>()).add(new Item(group, full, name, full.toLowerCase(Locale.ROOT)));
        }
        List<String> order = new ArrayList<>(textures.keySet());
        order.sort(Comparator.comparing((String k) -> !TEXTURE_LABELS.containsKey(k)).thenComparing(k -> textureGroups.get(k).label()));
        for (String key : order) index.put(textureGroups.get(key), sorted(textures.get(key)));

        Map<String, List<Item>> sounds = new TreeMap<>();
        Map<String, Group> soundGroups = new LinkedHashMap<>();
        for (Identifier id : client.getSoundManager().getAvailableSounds()) {
            String path = id.getPath();
            int dot = path.indexOf('.');
            String key = dot < 0 ? "misc" : path.substring(0, dot);
            String name = dot < 0 ? path : path.substring(dot + 1);
            Group group = soundGroups.computeIfAbsent(key, k -> new Group(Section.SOUNDS, k, pretty(k)));
            String full = id.toString();
            sounds.computeIfAbsent(key, k -> new ArrayList<>()).add(new Item(group, full, name, full.toLowerCase(Locale.ROOT)));
        }
        for (Map.Entry<String, List<Item>> entry : sounds.entrySet()) index.put(soundGroups.get(entry.getKey()), sorted(entry.getValue()));
        if (open != null) {
            Group previous = open;
            open = index.keySet().stream().filter(group -> group.equals(previous)).findFirst().orElse(null);
        }
    }

    private static List<Item> sorted(List<Item> items) {
        items.sort(Comparator.comparing(Item::name));
        return items;
    }

    private static String pretty(String key) {
        String spaced = key.replace('_', ' ');
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String fit(String name, float width) {
        if (ImGui.calcTextSize(name).x <= width) return name;
        for (int length = name.length() - 1; length >= 1; length--) {
            String candidate = name.substring(0, length) + "...";
            if (ImGui.calcTextSize(candidate).x <= width) return candidate;
        }
        return "...";
    }
}
