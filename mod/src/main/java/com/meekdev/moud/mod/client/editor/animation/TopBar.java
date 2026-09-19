package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.impl.ImGuiMCImpl;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

final class TopBar {

    private static final float HEIGHT = 40.0f;
    private static final float GROUP_PAD = 3.0f;
    private static final float SWATCH = 16.0f;
    private static final String RIG_POPUP = "##anim-rig-picker";

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private final PreviewRig rig;
    private final IconWidgets icons;

    TopBar(AnimationWorkspace workspace, AnimationSession session, PreviewRig rig, IconWidgets icons) {
        this.workspace = workspace;
        this.session = session;
        this.rig = rig;
        this.icons = icons;
    }

    float height() {
        return EditorScale.of(HEIGHT);
    }

    void render() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_HEADER_BACKGROUND);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorStyle.windowPadding() * 2, (height() - Toolbars.buttonHeight()) * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, EditorStyle.itemSpacingX(), 0.0f);
        ImGui.beginChild("##anim-top-bar", 0.0f, height(), ImGuiChildFlags.AlwaysUseWindowPadding, ImGuiWindowFlags.NoScrollbar);
        ImDrawList draw = ImGui.getWindowDrawList();
        float barLeft = ImGui.getWindowPosX();
        float barBottom = ImGui.getWindowPosY() + ImGui.getWindowHeight();
        draw.addLine(barLeft, barBottom - 1, barLeft + ImGui.getWindowWidth(), barBottom - 1, EditorStyle.COLOR_OUTLINE);
        Toolbars.pushFlatButtons();
        renderCrumbs(draw);
        renderTransport(draw);
        renderRight();
        Toolbars.popFlatButtons();
        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor();
    }

    private void renderCrumbs(ImDrawList draw) {
        float line = ImGui.getCursorPosY();
        float iconSize = EditorStyle.iconSizeMedium();
        ImGui.setCursorPosY(line + (Toolbars.buttonHeight() - iconSize) * 0.5f);
        icons.draw(EditorIcon.ANIMATION, iconSize);
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.setCursorPosY(line);
        ImGui.alignTextToFramePadding();
        EditorStyle.titleFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.titleFontPixelHeight()));
        ImGui.textUnformatted("Animator");
        EditorStyle.titleFont().ifPresent(font -> ImGui.popFont());
        String place = AssetFiles.root().getFileName() == null ? "place" : AssetFiles.root().getFileName().toString();
        crumb(place, false);
        crumb(ClipLibrary.FOLDER, false);
        crumb(session.hasClip() ? session.name() + ClipLibrary.EXTENSION : "no clip", true);
        if (session.dirty()) {
            ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY() + ImGui.getFrameHeight() * 0.5f;
            draw.addCircleFilled(x + EditorScale.of(3), y, EditorScale.of(3), EditorStyle.COLOR_WARNING);
            ImGui.dummy(EditorScale.of(6), ImGui.getFrameHeight());
            tooltip("Unsaved changes");
        }
        if (session.hasClip() && session.v1()) {
            ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
            ImGui.alignTextToFramePadding();
            Texts.colored(EditorStyle.COLOR_WARNING, "format 1, saving converts it");
        }
    }

    private static void crumb(String text, boolean last) {
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.alignTextToFramePadding();
        Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "/");
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.alignTextToFramePadding();
        Texts.colored(last ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED, text);
    }

    private void renderTransport(ImDrawList draw) {
        float button = EditorStyle.iconSizeToolbar() + EditorStyle.framePaddingX() * 2;
        float spacing = EditorScale.of(2);
        float pad = EditorScale.of(GROUP_PAD);
        String time = Paint.seconds(session.time());
        String rest = "/ " + Paint.seconds(session.clip().length) + " s";
        String frame = "f " + Math.round(session.time() * session.clip().fps);
        float readout = Paint.monoWidth(time) + Paint.monoWidth(rest) + Paint.monoWidth(frame) + EditorScale.of(22);
        float group = button * 5 + spacing * 4 + pad * 2;
        float width = ImGui.getWindowWidth();
        float start = (width - group - readout - EditorScale.of(16)) * 0.5f;
        ImGui.sameLine(Math.max(lastRight() + EditorScale.of(24), start));
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = Toolbars.buttonHeight();
        draw.addRectFilled(left, top - pad, left + group, top + height + pad, EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.frameRounding() + pad);
        ImGui.setCursorScreenPos(left + pad, top);
        boolean open = session.hasClip();
        ImGui.beginDisabled(!open);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, spacing, 0);
        if (icons.iconButton("anim-start", EditorIcon.PLAY_START_BACKWARDS, EditorStyle.iconSizeToolbar())) {
            session.stop();
            session.seek(0);
        }
        tooltip("Jump to start (Home)");
        ImGui.sameLine();
        if (icons.iconButton("anim-prev-key", EditorIcon.BACK, EditorStyle.iconSizeToolbar())) session.previousKey();
        tooltip("Previous key");
        ImGui.sameLine();
        boolean playing = session.playing();
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, EditorStyle.COLOR_ACCENT);
        boolean toggled = ImGui.imageButton("anim-play", icons.textureId(playing ? EditorIcon.PAUSE : EditorIcon.PLAY), EditorStyle.iconSizeToolbar(),
                EditorStyle.iconSizeToolbar(), 0, 0, 1, 1, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1);
        ImGui.popStyleColor(3);
        if (toggled) session.togglePlay();
        tooltip(playing ? "Pause (Space)" : "Play (Space)");
        ImGui.sameLine();
        if (icons.iconButton("anim-next-key", EditorIcon.FORWARD, EditorStyle.iconSizeToolbar())) session.nextKey();
        tooltip("Next key");
        ImGui.sameLine();
        if (icons.toggleButton("anim-loop", EditorIcon.LOOP, EditorStyle.iconSizeToolbar(), session.looping())) session.toggleLooping();
        tooltip("Loop playback in the editor");
        ImGui.popStyleVar();
        ImGui.endDisabled();
        ImGui.sameLine(0, EditorScale.of(14) + pad);
        float y = ImGui.getCursorScreenPosY() + (height - Paint.monoSize()) * 0.5f;
        float x = ImGui.getCursorScreenPosX();
        Paint.mono(draw, x, y, EditorStyle.COLOR_TEXT_FOCUS, time);
        x += Paint.monoWidth(time) + EditorScale.of(6);
        Paint.mono(draw, x, y, EditorStyle.COLOR_TEXT_MUTED, rest);
        x += Paint.monoWidth(rest) + EditorScale.of(12);
        Paint.mono(draw, x, y, EditorStyle.COLOR_TEXT_MUTED, frame);
        ImGui.dummy(readout, height);
    }

    private void renderRight() {
        AnimClip clip = session.clip();
        List<String> spaces = List.of("Body", "View");
        float segments = SegmentedControl.width(spaces);
        String label = rig.label();
        float picker = EditorScale.of(SWATCH) + ImGui.calcTextSizeX(label) + EditorScale.of(40);
        float save = Dialogs.buttonWidth() * 0.75f;
        float total = segments + picker + save + EditorStyle.itemSpacingX() * 6;
        ImGui.sameLine(Math.max(lastRight() + EditorScale.of(24), ImGui.getWindowWidth() - total - EditorStyle.windowPadding() * 2));
        float line = ImGui.getCursorPosY();
        ImGui.setCursorPosY(line + (Toolbars.buttonHeight() - SegmentedControl.height()) * 0.5f);
        ImGui.beginDisabled(!session.hasClip());
        int chosen = SegmentedControl.render("anim-space", spaces, clip.space == AnimClip.Space.VIEW ? 1 : 0);
        ImGui.endDisabled();
        tooltip("The clip's space: the whole body, or the first person view model");
        if (session.hasClip() && chosen != (clip.space == AnimClip.Space.VIEW ? 1 : 0)) {
            AnimClip.Space space = chosen == 1 ? AnimClip.Space.VIEW : AnimClip.Space.BODY;
            session.edit(chosen == 1 ? "Use view space" : "Use body space", changed -> {
                changed.space = space;
                changed.rig = space == AnimClip.Space.VIEW ? "view" : "player";
            });
            session.joint(null);
            session.selectKeys(Set.of());
            if (space == AnimClip.Space.BODY) workspace.frameRig();
        }
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.setCursorPosY(line);
        renderRigPicker(picker);
        ImGui.sameLine(0, EditorStyle.itemSpacingX() * 2);
        ImGui.setCursorPosY(line);
        if (Dialogs.primaryButton("Save##anim-save", session.hasClip() && session.dirty())) session.save();
        tooltip("Write the clip to its .anim file (Ctrl+S)");
    }

    private void renderRigPicker(float width) {
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_WIDGET_BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_WIDGET_HOVER);
        boolean clicked = ImGui.button("##anim-rig", width, Toolbars.buttonHeight());
        ImGui.popStyleColor(2);
        float left = ImGui.getItemRectMinX();
        float top = ImGui.getItemRectMinY();
        float height = ImGui.getItemRectMaxY() - top;
        ImDrawList draw = ImGui.getWindowDrawList();
        float swatch = EditorScale.of(SWATCH);
        float x = left + EditorStyle.framePaddingX();
        float y = top + (height - swatch) * 0.5f;
        drawFace(draw, x, y, swatch);
        draw.addText(x + swatch + EditorScale.of(8), top + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT, rig.label());
        float chevron = left + width - EditorScale.of(14);
        float middle = top + height * 0.5f;
        float reach = EditorScale.of(3);
        draw.addTriangleFilled(chevron - reach, middle - reach * 0.6f, chevron + reach, middle - reach * 0.6f, chevron, middle + reach * 0.8f, EditorStyle.COLOR_TEXT_MUTED);
        if (clicked) ImGui.openPopup(RIG_POPUP);
        tooltip("The rig the clip is previewed on");
        if (!ImGui.beginPopup(RIG_POPUP)) return;
        Texts.muted("Preview character");
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ImGui.menuItem("Your skin", "", !rig.borrowing() && rig.ownSkin())) {
            rig.skin(null);
        }
        for (PreviewRig.Skin skin : PreviewRig.SKINS) {
            if (ImGui.menuItem(skin.label() + (skin.slim() ? "  slim" : ""), "", !rig.borrowing() && !rig.ownSkin() && rig.skin() == skin)) rig.skin(skin);
        }
        List<Character> characters = workspace.sceneCharacters();
        if (!characters.isEmpty()) {
            ImGui.separator();
            Texts.muted("Characters in the scene");
            for (Character character : characters) {
                if (ImGui.menuItem(character.name() + "##character-" + character.id(), "", rig.body() == character)) rig.borrow(character);
            }
        }
        ImGui.separator();
        if (ImGui.menuItem("Move preview in front of the camera")) workspace.restage();
        ImGui.endPopup();
    }

    private void drawFace(ImDrawList draw, float x, float y, float size) {
        String skin = rig.ownSkin() && Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getSkin().body().texturePath().toString() : rig.skin().texture();
        Identifier id = Identifier.tryParse(skin);
        AbstractTexture texture = id == null ? null : Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || rig.borrowing()) {
            draw.addRectFilled(x, y, x + size, y + size, EditorStyle.COLOR_WIDGET_OUTLINE, EditorScale.of(3));
            return;
        }
        long handle = ImGuiMCImpl.handler.getRenderer().getImGuiId(ImGuiMC.getTexture(texture), null);
        draw.addImage(handle, x, y, x + size, y + size, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f);
        draw.addImage(handle, x, y, x + size, y + size, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f);
    }

    private static float lastRight() {
        return ImGui.getItemRectMaxX() - ImGui.getWindowPosX();
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }
}
