package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class RigPanel implements Panel {

    static final String ID = "anim-rig";
    private static final String NEW_CLIP = "New clip##anim-new-clip";

    private record Control(String name, String kind, String target, EditorIcon icon) {}

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private final PreviewRig rig;
    private final IconWidgets icons;
    private final ImString filter = new ImString(64);
    private final ImString newName = new ImString(64);
    private int newSpace;
    private boolean askNew;

    RigPanel(AnimationWorkspace workspace, AnimationSession session, PreviewRig rig, IconWidgets icons) {
        this.workspace = workspace;
        this.session = session;
        this.rig = rig;
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Rig";
    }

    @Override
    public void render() {
        renderRig();
        renderControls();
        renderClips();
        renderNewClip();
    }

    private void renderRig() {
        SearchField.render("##anim-joint-filter", "Filter joints", filter, ImGui.getContentRegionAvailX());
        ImGui.separator();
        AnimClip clip = session.clip();
        String query = filter.get().strip().toLowerCase(Locale.ROOT);
        String rigName = clip.space == AnimClip.Space.VIEW ? "View" : rig.borrowing() && rig.body() != null ? rig.body().name() : "Player";
        ListRow.draw(icons, "anim-rig-root", 0, EditorIcon.SKELETON_3D, rigName, "", false, true, EditorStyle.COLOR_TEXT_FAINT);
        for (Rigs.Joint joint : Rigs.of(clip)) {
            if (!query.isEmpty() && !joint.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            int count = clip.keyCount(joint.name());
            boolean selected = joint.name().equals(session.joint());
            if (ListRow.draw(icons, "anim-joint-" + joint.name(), joint.depth() + 1, EditorIcon.BONE, joint.name(), count == 0 ? "" : String.valueOf(count),
                    selected, count > 0, selected ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_FAINT)) {
                session.joint(joint.name());
            }
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                if (!session.expanded().remove(joint.name())) session.expanded().add(joint.name());
            }
        }
        ImGui.dummy(0, EditorScale.of(4));
    }

    private void renderControls() {
        if (!Sections.header("Controls")) return;
        List<Control> controls = controls();
        if (controls.isEmpty()) {
            ImGui.indent(EditorScale.of(6));
            Texts.muted(session.clip().space == AnimClip.Space.VIEW ? "The view rig has no controls" : "No IK or spring controls on this rig");
            ImGui.unindent(EditorScale.of(6));
        }
        for (Control control : controls) {
            ListRow.draw(icons, "anim-control-" + control.name(), 0, control.icon(), control.name(),
                    control.kind() + (control.target().isEmpty() ? "" : " · " + control.target()), false, true, EditorStyle.COLOR_TEXT_FAINT);
        }
        ImGui.dummy(0, EditorScale.of(4));
    }

    private List<Control> controls() {
        List<Control> found = new ArrayList<>();
        Character body = rig.body();
        if (body == null || session.clip().space == AnimClip.Space.VIEW) return found;
        collect(body, found);
        return found;
    }

    private static void collect(Instance under, List<Control> found) {
        for (Instance child : under.children()) {
            String kind = kind(child.def().name());
            if (kind != null) found.add(new Control(child.name(), kind, target(child), kind.equals("spring") ? EditorIcon.SPRING_BONE_SIMULATOR_3D : EditorIcon.LOOK_AT_MODIFIER_3D));
            collect(child, found);
        }
    }

    private static @Nullable String kind(String className) {
        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.contains("spring")) return "spring";
        if (lower.contains("ik") && lower.endsWith("control") || lower.contains("pointat") || lower.contains("lookat") || lower.contains("aim")) return "aim";
        return null;
    }

    private static String target(Instance control) {
        for (PropertyDef property : control.def().properties()) {
            if (property == null) continue;
            String name = property.name().toLowerCase(Locale.ROOT);
            if (!name.contains("joint") && !name.equals("part1") && !name.equals("target") && !name.equals("chain")) continue;
            Object value = property.getObj(control);
            if (value instanceof Instance instance) return instance.name();
            if (property.type() == PropertyType.STRING && value instanceof String text && !text.isEmpty()) return text;
        }
        return "";
    }

    private void renderClips() {
        boolean open = Sections.header("Clips");
        float right = ImGui.getItemRectMaxX();
        float top = ImGui.getItemRectMinY();
        float height = ImGui.getItemRectMaxY() - top;
        float size = EditorStyle.iconSizeSmall();
        ImGui.getWindowDrawList().addImage(icons.textureId(EditorIcon.ADD), right - size - EditorScale.of(6), top + (height - size) * 0.5f,
                right - EditorScale.of(6), top + (height + size) * 0.5f);
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && ImGui.isMouseHoveringRect(right - size - EditorScale.of(10), top, right, top + height)) {
            askNew = true;
        }
        if (!open) return;
        List<ClipLibrary.Listed> clips = session.library().clips();
        if (clips.isEmpty()) {
            ImGui.indent(EditorScale.of(6));
            Texts.muted("No clips in animations/ yet");
            if (ImGui.button("New clip##anim-first-clip")) askNew = true;
            ImGui.unindent(EditorScale.of(6));
        }
        for (ClipLibrary.Listed clip : clips) {
            boolean current = clip.path().equals(session.path());
            ClipLibrary.Open loaded = session.library().get(clip.path());
            String meta = clip.error() != null ? "broken" : clip.space() == AnimClip.Space.VIEW ? "view" : Paint.number(clip.length()) + " s";
                        int metaColor = clip.error() != null ? EditorStyle.COLOR_DANGER : loaded != null && loaded.dirty() ? EditorStyle.COLOR_WARNING : EditorStyle.COLOR_TEXT_FAINT;
            if (ListRow.draw(icons, "anim-clip-" + clip.path(), 0, null, clip.name(), meta, current, true, metaColor)) {
                if (clip.error() == null) session.open(clip.path());
            }
            if (clip.error() != null && ImGui.isItemHovered()) ImGui.setTooltip(clip.error());
        }
    }

    private void renderNewClip() {
        if (askNew) {
            askNew = false;
            newName.set("");
            newSpace = session.clip().space == AnimClip.Space.VIEW ? 1 : 0;
            ImGui.openPopup(NEW_CLIP);
        }
        if (!Dialogs.begin(NEW_CLIP, 360)) return;
        Dialogs.title("New clip");
        Texts.muted("Saved as animations/<name>.anim");
        Dialogs.gap();
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean entered = ImGui.inputTextWithHint("##anim-new-name", "wave", newName, ImGuiInputTextFlags.EnterReturnsTrue);
        Dialogs.gap();
        newSpace = SegmentedControl.render("anim-new-space", List.of("Body", "View"), newSpace);
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##anim-new-cancel")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        boolean ready = !newName.get().isBlank();
        if (Dialogs.primaryButton("Create##anim-new-create", ready) || entered && ready) {
            session.create(newName.get(), newSpace == 1 ? AnimClip.Space.VIEW : AnimClip.Space.BODY);
            ImGui.closeCurrentPopup();
        }
        Dialogs.end();
    }
}
