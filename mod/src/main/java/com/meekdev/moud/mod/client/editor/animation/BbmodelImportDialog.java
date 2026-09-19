package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class BbmodelImportDialog {

    private static final String POPUP = "##anim-bbmodel-import";
    private static final float WIDTH = 900.0f;
    private static final float HEIGHT = 600.0f;
    private static final float PREVIEW_WIDTH = 300.0f;
    private static final float HEADER = 56.0f;
    private static final float FOOTER = 56.0f;
    private static final int POPUP_FLAGS = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoScrollbar;
    private static final int GRID = EditorStyle.withAlpha(EditorStyle.rgb(255, 255, 255), 0.06f);
    private static final int CUBE_EDGE = EditorStyle.withAlpha(EditorStyle.rgb(0, 0, 0), 0.35f);
    private static final int UNMAPPED_FILL = EditorStyle.rgb(92, 92, 96);
    private static final int MAPPED_FILL = EditorStyle.rgb(136, 138, 146);
    private static final List<String> TABS = List.of("Bones", "Animations", "Options");
    private static final List<String> JOINTS = List.of("", "head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "cape", "rightWing", "leftWing");

    private final ModelImport importer;
    private final IconWidgets icons;
    private final AnimationWorkspace workspace;
    private @Nullable Path file;
    private ModelImport.@Nullable Summary summary;
    private @Nullable String error;
    private ModelImport.@Nullable Outcome outcome;
    private final Map<String, String> joints = new LinkedHashMap<>();
    private final Set<String> chosen = new LinkedHashSet<>();
    private final ImString folder = new ImString(128);
    private boolean model = true;
    private boolean opening;
    private int tab;
    private float previewTime;

    BbmodelImportDialog(ModelImport importer, IconWidgets icons, AnimationWorkspace workspace) {
        this.importer = importer;
        this.icons = icons;
        this.workspace = workspace;
    }

    void open(Path path) {
        file = path;
        error = null;
        outcome = null;
        summary = null;
        joints.clear();
        chosen.clear();
        tab = 0;
        try {
            summary = importer.inspect(path);
            for (ModelImport.Group group : summary.groups()) joints.put(group.uuid(), group.joint());
            for (ModelImport.Animation animation : summary.animations()) {
                if (!animation.empty()) chosen.add(animation.name());
            }
        } catch (IOException | RuntimeException e) {
            error = e.getMessage() == null ? "the file could not be read" : e.getMessage();
        }
        String stem = path.getFileName().toString().replaceAll("(?i)\\.bbmodel$", "");
        folder.set(ClipLibrary.FOLDER + "/" + stem + "/");
        opening = true;
    }

    private boolean closing;

    void close() {
        closing = true;
    }

    void confirm() {
        if (file != null && summary != null && outcome == null) run();
    }

    void render() {
        if (opening) {
            opening = false;
            ImGui.openPopup(POPUP);
        }
        float width = Math.min(EditorScale.of(WIDTH), ImGui.getMainViewport().getWorkSizeX() - EditorScale.of(40));
        float height = Math.min(EditorScale.of(HEIGHT), ImGui.getMainViewport().getWorkSizeY() - EditorScale.of(40));
        ImGui.setNextWindowPos(ImGui.getMainViewport().getCenterX(), ImGui.getMainViewport().getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        boolean open = ImGui.beginPopupModal(POPUP, POPUP_FLAGS);
        ImGui.popStyleVar();
        if (!open) return;
        if (closing) {
            closing = false;
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        renderHeader(width);
        float body = height - EditorScale.of(HEADER) - EditorScale.of(FOOTER);
        if (summary != null) {
            renderPreview(body);
            ImGui.sameLine(0, 0);
            renderTabs(width - EditorScale.of(PREVIEW_WIDTH), body);
        } else {
            ImGui.beginChild("##bb-error", width, body, false);
            ImGui.setCursorPos(EditorScale.of(20), EditorScale.of(20));
            ImGui.pushTextWrapPos(width - EditorScale.of(20));
            Notices.danger("Could not read " + (file == null ? "the file" : file.getFileName()) + ": " + error);
            ImGui.popTextWrapPos();
            ImGui.endChild();
        }
        renderFooter(width);
        ImGui.endPopup();
    }

    private void renderHeader(float width) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = EditorScale.of(HEADER);
        draw.addLine(left, top + height - 1, left + width, top + height - 1, EditorStyle.COLOR_OUTLINE);
        float icon = EditorStyle.iconSizeMedium();
        float x = left + EditorScale.of(20);
        draw.addImage(icons.textureId(EditorIcon.MESH), x, top + (height - icon) * 0.5f, x + icon, top + (height + icon) * 0.5f);
        x += icon + EditorScale.of(12);
        String title = "Import " + (file == null ? "" : file.getFileName());
        float titleSize = EditorStyle.titleFontPixelHeight();
        float titleY = top + height * 0.5f - titleSize - EditorScale.of(1);
        draw.addText(EditorStyle.titleFont().orElse(ImGui.getFont()), Math.round(titleSize), x, titleY, EditorStyle.COLOR_TEXT_FOCUS, title);
        ModelImport.Summary known = summary;
        if (known != null) {
            String subtitle = known.format() + (known.playerRig() ? " · player template" : "") + " · " + known.groups().size() + " groups · "
                    + known.animations().size() + (known.animations().size() == 1 ? " animation" : " animations");
            Paint.small(draw, x, top + height * 0.5f + EditorScale.of(2), EditorStyle.COLOR_TEXT_MUTED, subtitle);
            String badge = "Player rig matched " + known.matched() + " / " + known.wanted();
            int color = badgeColor(known);
            float badgeWidth = ImGui.calcTextSizeX(badge) + EditorScale.of(20);
            float badgeHeight = ImGui.getFrameHeight();
            float bx = left + width - badgeWidth - EditorScale.of(20);
            float by = top + (height - badgeHeight) * 0.5f;
            draw.addRectFilled(bx, by, bx + badgeWidth, by + badgeHeight, EditorStyle.withAlpha(color, 0.14f), EditorStyle.frameRounding());
            draw.addRect(bx, by, bx + badgeWidth, by + badgeHeight, EditorStyle.withAlpha(color, 0.45f), EditorStyle.frameRounding());
            draw.addText(bx + EditorScale.of(10), by + (badgeHeight - ImGui.getTextLineHeight()) * 0.5f, color, badge);
        }
        ImGui.dummy(width, height);
    }

    private static int badgeColor(ModelImport.Summary known) {
        if (known.playerRig()) return EditorStyle.COLOR_SUCCESS;
        if (known.matched() > 0) return EditorStyle.COLOR_WARNING;
        return EditorStyle.COLOR_TEXT_MUTED;
    }

    private void renderPreview(float height) {
        float width = EditorScale.of(PREVIEW_WIDTH);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        ImGui.beginChild("##bb-preview", width, height, false, ImGuiWindowFlags.NoScrollbar);
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getWindowPosX();
        float top = ImGui.getWindowPosY();
        float footer = EditorScale.of(56);
        drawModel(draw, left, top, width, height - footer);
        float y = top + height - footer;
        draw.addLine(left, y, left + width, y, EditorStyle.COLOR_OUTLINE);
        ModelImport.Animation shown = firstChosen();
        if (shown != null) {
            previewTime += ImGui.getIO().getDeltaTime();
            double length = Math.max(0.05, shown.length());
            double at = previewTime % length;
            float pad = EditorScale.of(16);
            draw.addText(left + pad, y + EditorScale.of(12), EditorStyle.COLOR_TEXT_MUTED, "Preview");
            String label = shown.name() + " · " + Paint.seconds(shown.length()) + " s";
            Paint.mono(draw, left + width - pad - Paint.monoWidth(label), y + EditorScale.of(12), EditorStyle.COLOR_TEXT, label);
            float barY = y + EditorScale.of(38);
            float barBottom = barY + EditorScale.of(3);
            float progress = (float) ((width - pad * 2) * at / length);
            draw.addRectFilled(left + pad, barY, left + width - pad, barBottom, EditorStyle.COLOR_WIDGET_BACKGROUND, EditorScale.of(2));
            draw.addRectFilled(left + pad, barY, left + pad + progress, barBottom, EditorStyle.COLOR_ACCENT, EditorScale.of(2));
        }
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private ModelImport.@Nullable Animation firstChosen() {
        if (summary == null) return null;
        for (ModelImport.Animation animation : summary.animations()) {
            if (chosen.contains(animation.name())) return animation;
        }
        return summary.animations().isEmpty() ? null : summary.animations().getFirst();
    }

    private void drawModel(ImDrawList draw, float left, float top, float width, float height) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ModelImport.Group group : summary.groups()) {
            for (ModelImport.Cube cube : group.shape()) {
                minX = Math.min(minX, Math.min(cube.from().x(), cube.to().x()));
                maxX = Math.max(maxX, Math.max(cube.from().x(), cube.to().x()));
                minY = Math.min(minY, Math.min(cube.from().y(), cube.to().y()));
                maxY = Math.max(maxY, Math.max(cube.from().y(), cube.to().y()));
            }
        }
        float floor = top + height * 0.72f;
        float centre = left + width * 0.5f;
        for (int n = -3; n <= 3; n++) draw.addLine(centre, floor, centre + n * width * 0.45f, top + height, GRID);
        draw.addLine(left, floor, left + width, floor, GRID);
        float lower = floor + (top + height - floor) * 0.45f;
        draw.addLine(left, lower, left + width, lower, GRID);
        if (minX == Double.POSITIVE_INFINITY) {
            String empty = "No cubes to show";
            draw.addText(left + (width - ImGui.calcTextSizeX(empty)) * 0.5f, top + height * 0.5f, EditorStyle.COLOR_TEXT_MUTED, empty);
            return;
        }
        double span = Math.max(maxX - minX, maxY - minY);
        float scale = (float) (Math.min(width * 0.6, height * 0.62) / Math.max(1, span));
        float originX = centre - (float) ((minX + maxX) * 0.5 * scale);
        float originY = floor + (float) (minY * scale);
        for (ModelImport.Group group : summary.groups()) {
            String joint = joints.getOrDefault(group.uuid(), "");
            int fill = joint.isEmpty() ? UNMAPPED_FILL : MAPPED_FILL;
            for (ModelImport.Cube cube : group.shape()) {
                float x0 = originX + (float) (Math.min(cube.from().x(), cube.to().x()) * scale);
                float x1 = originX + (float) (Math.max(cube.from().x(), cube.to().x()) * scale);
                float y0 = originY - (float) (Math.max(cube.from().y(), cube.to().y()) * scale);
                float y1 = originY - (float) (Math.min(cube.from().y(), cube.to().y()) * scale);
                draw.addRectFilled(x0, y0, x1, y1, fill);
                draw.addRect(x0, y0, x1, y1, CUBE_EDGE);
            }
        }
        for (ModelImport.Group group : summary.groups()) {
            Vector3 pivot = group.pivot();
            float px = originX + (float) (pivot.x() * scale);
            float py = originY - (float) (pivot.y() * scale);
            draw.addCircleFilled(px, py, EditorScale.of(3), Paint.SELECTED);
        }
    }

    private void renderTabs(float width, float height) {
        ImGui.beginChild("##bb-tabs", width, height, false);
        float pad = EditorScale.of(20);
        ImGui.setCursorPos(pad, EditorScale.of(10));
        ImDrawList draw = ImGui.getWindowDrawList();
        for (int n = 0; n < TABS.size(); n++) {
            if (n > 0) ImGui.sameLine(0, EditorScale.of(20));
            String label = TABS.get(n);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            float labelWidth = ImGui.calcTextSizeX(label);
            float frame = ImGui.getFrameHeight();
            int color = n == tab ? EditorStyle.COLOR_TEXT_FOCUS : EditorStyle.COLOR_TEXT_MUTED;
            ImGui.pushStyleColor(ImGuiCol.Text, color);
            if (ImGui.invisibleButton("##bb-tab-" + n, labelWidth, frame)) tab = n;
            ImGui.popStyleColor();
            draw.addText(x, y + (frame - ImGui.getTextLineHeight()) * 0.5f, color, label);
            if (n == tab) draw.addRectFilled(x, y + frame + EditorScale.of(2), x + labelWidth, y + frame + EditorScale.of(4), Paint.SELECTED);
        }
        float lineY = ImGui.getCursorScreenPosY() + EditorScale.of(4);
        draw.addLine(ImGui.getWindowPosX(), lineY, ImGui.getWindowPosX() + width, lineY, EditorStyle.COLOR_OUTLINE);
        ImGui.dummy(0, EditorScale.of(14));
        ImGui.indent(pad);
        ImGui.pushTextWrapPos(width - pad);
        if (outcome != null) renderOutcome(outcome);
        else switch (tab) {
            case 0 -> {
                renderBones(width - pad * 2);
                ImGui.dummy(0, EditorScale.of(10));
                renderAnimations(width - pad * 2);
                renderNote();
            }
            case 1 -> {
                renderAnimations(width - pad * 2);
                renderNote();
            }
            default -> renderOptions(width - pad * 2);
        }
        ImGui.popTextWrapPos();
        ImGui.unindent(pad);
        ImGui.endChild();
    }

    private void renderOutcome(ModelImport.Outcome done) {
        Sections.caption(done.done() ? "IMPORTED" : "NOT IMPORTED");
        ImGui.textUnformatted(done.message());
        ImGui.dummy(0, EditorScale.of(8));
        for (Path written : done.written()) {
            Paint.pushMono();
            Texts.muted(AnimationSession.res(written));
            Paint.popMono();
        }
        if (done.notes().isEmpty()) return;
        ImGui.dummy(0, EditorScale.of(10));
        Sections.caption("NOTES");
        for (String note : done.notes()) Texts.colored(EditorStyle.COLOR_WARNING, note);
    }

    private void renderBones(float width) {
        float[] columns = {0, width * 0.32f, width * 0.64f, width * 0.88f};
        String[] titles = {"BLOCKBENCH GROUP", "MOUD JOINT", "PIVOT", "CUBES"};
        float startX = ImGui.getCursorPosX();
        for (int n = 0; n < titles.length; n++) {
            if (n > 0) ImGui.sameLine(startX + columns[n]);
            Sections.caption(titles[n]);
        }
        ImDrawList draw = ImGui.getWindowDrawList();
        for (ModelImport.Group group : summary.groups()) {
            ImGui.pushID(group.uuid());
            float rowTop = ImGui.getCursorScreenPosY();
            float rowLeft = ImGui.getCursorScreenPosX();
            float rowHeight = ImGui.getFrameHeight() + EditorScale.of(8);
            float rowBottom = rowTop + rowHeight;
            draw.addRectFilled(rowLeft - EditorScale.of(8), rowTop, rowLeft + width, rowBottom, EditorStyle.COLOR_ELEVATED_BACKGROUND, EditorStyle.frameRounding());
            ImGui.setCursorScreenPos(rowLeft, rowTop + EditorScale.of(4));
            ImGui.alignTextToFramePadding();
            Paint.pushMono();
            ImGui.textUnformatted("  ".repeat(group.depth()) + group.name());
            Paint.popMono();
            ImGui.sameLine(startX + columns[1]);
            String joint = joints.getOrDefault(group.uuid(), "");
            float dotX = ImGui.getCursorScreenPosX();
            float dotY = ImGui.getCursorScreenPosY() + ImGui.getFrameHeight() * 0.5f;
            int dot = joint.isEmpty() ? EditorStyle.COLOR_TEXT_FAINT : EditorStyle.COLOR_SUCCESS;
            draw.addCircleFilled(dotX + EditorScale.of(3), dotY, EditorScale.of(3), dot);
            ImGui.setCursorScreenPos(dotX + EditorScale.of(12), ImGui.getCursorScreenPosY());
            ImGui.setNextItemWidth(columns[2] - columns[1] - EditorScale.of(24));
            if (ImGui.beginCombo("##joint", joint.isEmpty() ? "new bone" : joint)) {
                for (String option : JOINTS) {
                    if (ImGui.selectable(option.isEmpty() ? "new bone" : option, option.equals(joint))) joints.put(group.uuid(), option);
                }
                ImGui.endCombo();
            }
            ImGui.sameLine(startX + columns[2]);
            Paint.pushMono();
            ImGui.alignTextToFramePadding();
            Texts.muted(trim(group.pivot().x()) + " " + trim(group.pivot().y()) + " " + trim(group.pivot().z()));
            ImGui.sameLine(startX + columns[3]);
            ImGui.alignTextToFramePadding();
            Texts.muted(String.valueOf(group.cubes()));
            Paint.popMono();
            ImGui.setCursorScreenPos(rowLeft, rowTop + rowHeight + EditorScale.of(4));
            ImGui.dummy(0, 0);
            ImGui.popID();
        }
    }

    private static String trim(double value) {
        return ClipFile.number(Math.round(value * 100) / 100.0);
    }

    private void renderAnimations(float width) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getCursorScreenPosX() - EditorScale.of(8);
        float top = ImGui.getCursorScreenPosY();
        float rowHeight = ImGui.getFrameHeight() + EditorScale.of(4);
        float height = EditorScale.of(34) + rowHeight * Math.max(1, summary.animations().size()) + EditorScale.of(8);
        draw.addRect(left, top, left + width + EditorScale.of(8), top + height, EditorStyle.COLOR_OUTLINE, EditorStyle.frameRounding());
        ImGui.setCursorScreenPos(left + EditorScale.of(16), top + EditorScale.of(12));
        Sections.caption("ANIMATIONS");
        float start = ImGui.getCursorPosX();
        if (summary.animations().isEmpty()) Texts.muted("This model has no animations");
        for (ModelImport.Animation animation : summary.animations()) {
            ImGui.setCursorPosX(start);
            boolean on = chosen.contains(animation.name());
            if (ImGui.checkbox("##anim-" + animation.name(), on)) {
                if (on) chosen.remove(animation.name());
                else chosen.add(animation.name());
            }
            ImGui.sameLine();
            Paint.pushMono();
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted(animation.name());
            ImGui.sameLine(start + width * 0.3f);
            ImGui.alignTextToFramePadding();
            Texts.muted(Paint.seconds(animation.length()) + " s");
            Paint.popMono();
            ImGui.sameLine(start + width * 0.45f);
            ImGui.alignTextToFramePadding();
            Texts.muted(animation.loop());
            ImGui.sameLine(start + width * 0.6f);
            ImGui.alignTextToFramePadding();
            if (!on) Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "skipped");
            else if (animation.empty()) Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "no keys");
            else if (animation.molang() > 0) Texts.colored(EditorStyle.COLOR_WARNING, count(animation.molang(), "Molang key") + " baked to numbers");
            else Texts.colored(EditorStyle.COLOR_TEXT_MUTED, "ready");
        }
        ImGui.setCursorScreenPos(left + EditorScale.of(8), top + height + EditorScale.of(6));
        ImGui.dummy(0, 0);
    }

    private void renderNote() {
        String first = firstChosen() == null ? "clip" : firstChosen().name();
        Paint.pushSmall();
        Texts.muted("Each group becomes a Bone with its cubes as rigid parts. Clips are written to " + folder.get().strip()
                + " and play on every player: body.animator:loadAnimation(" + first + ")");
        Paint.popSmall();
    }

    private void renderOptions(float width) {
        Texts.muted("Folder for the clips");
        ImGui.setNextItemWidth(width);
        Paint.pushMono();
        ImGui.inputText("##bb-folder", folder);
        Paint.popMono();
        ImGui.dummy(0, EditorScale.of(8));
        if (ImGui.checkbox("Import the model too##bb-model", model)) model = !model;
        Sections.caption("Off writes only the clips, for a model that is already in the place");
        ImGui.dummy(0, EditorScale.of(8));
        Sections.caption("16 px = 1 block, Y up, textures packed into the place");
    }

    private void renderFooter(float width) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = EditorScale.of(FOOTER);
        draw.addLine(left, top, left + width, top, EditorStyle.COLOR_OUTLINE);
        float pad = EditorScale.of(20);
        String note = outcome != null ? outcome.message() : "16 px = 1 block · Y up · textures packed into the place";
        int color = noteColor();
        draw.addText(left + pad, top + (height - ImGui.getTextLineHeight()) * 0.5f, color, note);
        int clips = chosenClips();
        String action = model ? "Import model and " + count(clips, "clip") : "Import " + count(clips, "clip");
        float actionWidth = ImGui.calcTextSizeX(action) + EditorScale.of(28);
        float cancelWidth = Dialogs.buttonWidth();
        float buttonHeight = ImGui.getFrameHeight() + EditorScale.of(6);
        float buttonsLeft = left + width - pad - actionWidth - cancelWidth - EditorStyle.itemSpacingX() * 2;
        ImGui.setCursorScreenPos(buttonsLeft, top + (height - buttonHeight) * 0.5f);
        String cancel = outcome != null && outcome.done() ? "Close##bb-close" : "Cancel##bb-cancel";
        if (ImGui.button(cancel, cancelWidth, buttonHeight)) ImGui.closeCurrentPopup();
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_ON_ACCENT);
        boolean ready = summary != null && (clips > 0 || model) && (outcome == null || !outcome.done());
        ImGui.beginDisabled(!ready);
        boolean go = ImGui.button(action + "##bb-go", actionWidth, buttonHeight);
        ImGui.endDisabled();
        ImGui.popStyleColor(3);
        if (go && file != null && summary != null) run();
        ImGui.setCursorScreenPos(left, top + height);
        ImGui.dummy(0, 0);
    }

    private int noteColor() {
        if (outcome == null) return EditorStyle.COLOR_TEXT_MUTED;
        if (outcome.done()) return EditorStyle.COLOR_SUCCESS;
        return EditorStyle.COLOR_WARNING;
    }

    private int chosenClips() {
        if (summary == null) return 0;
        int clips = 0;
        for (ModelImport.Animation animation : summary.animations()) {
            if (chosen.contains(animation.name())) clips++;
        }
        return clips;
    }

    private static String count(int amount, String noun) {
        return amount + " " + noun + (amount == 1 ? "" : "s");
    }

    private void run() {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (ModelImport.Group group : summary.groups()) mapping.put(group.name(), joints.getOrDefault(group.uuid(), ""));
        String typed = folder.get().strip();
        String target = typed.toLowerCase(Locale.ROOT).startsWith("res://") ? typed.substring("res://".length()) : typed;
        ModelImport.Choices choices = new ModelImport.Choices(mapping, Set.copyOf(chosen), target, model);
        try {
            outcome = importer.run(file, summary, choices);
        } catch (RuntimeException e) {
            outcome = new ModelImport.Outcome(false, "import failed: " + e.getMessage(), List.of(), List.of());
        }
        workspace.importFinished(outcome);
    }
}
