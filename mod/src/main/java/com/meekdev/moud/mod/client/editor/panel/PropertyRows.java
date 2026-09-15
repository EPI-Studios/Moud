package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.assets.AssetsPanel;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.PendingEdits;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.kit.NumberFields;
import com.meekdev.moud.mod.client.editor.kit.Switches;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class PropertyRows {

    private static final float DRAG_STEP = 0.05f;
    private static final float LABEL_SHARE = 0.36f;
    private static final float LABEL_MIN = 84.0f;
    private static final float LABEL_MAX = 150.0f;
    private static final float DEGREE_STEP = 0.5f;
    private static final float RADIANS_TO_DEGREES = 57.295776f;
    private static final float DEGREES_TO_RADIANS = 0.017453293f;
    private static final float QUAT_EPSILON = 0.0001f;
    private static final int STRING_CAPACITY = 512;
    private static final int REF_CHOICES = 400;
    private static final String[] UDIM_LABELS = {"scale", "px"};
    private static final float[] UDIM_STEPS = {0.01f, 1.0f};
    private static final int COLOR_EDIT_FLAGS = ImGuiColorEditFlags.DisplayHex | ImGuiColorEditFlags.AlphaBar
            | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.PickerHueWheel;

    private static final class Euler {
        final float[] degrees = new float[3];
        @Nullable Quat seen;
    }

    private final SceneDocument document;
    private final Map<String, ImString> strings = new HashMap<>();
    private final Map<String, Euler> eulers = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private @Nullable String typing;
    private List<Instance> targets = List.of();
    private boolean mixed;
    private @Nullable String doc;

    PropertyRows(SceneDocument document) {
        this.document = document;
    }

    void beginFrame() {
        seen.clear();
    }

    void pruneStaleKeys() {
        strings.keySet().retainAll(seen);
        eulers.keySet().retainAll(seen);
    }

    void render(Instance instance, PropertyDef property, List<Instance> selected) {
        targets = selected;
        mixed = isMixed(instance, property);
        doc = PropertyDocs.of(instance.def(), property.name());
        String key = instance.id() + ":" + property.index();
        seen.add(key);
        ImGui.pushID(property.name());
        ImGui.beginDisabled(property.driven());
        switch (property.type()) {
            case BOOL -> renderBoolean(instance, property);
            case INT -> renderInt(instance, property);
            case NUM -> renderNumber(instance, property);
            case STRING, ASSET -> renderString(instance, property, key);
            case VEC3 -> renderVector3(instance, property);
            case QUAT -> renderQuaternion(instance, property, key);
            case CFRAME -> renderCFrame(instance, property, key);
            case COLOR -> renderColor(instance, property);
            case UDIM2 -> renderUDim2(instance, property);
            case ENUM -> renderEnum(instance, property);
            case REF -> renderRef(instance, property);
        }
        ImGui.endDisabled();
        if (property.driven() && ImGui.isItemHovered()) ImGui.setTooltip("Driven by the engine");
        ImGui.popID();
    }

    private void commit(Instance instance, PropertyDef property, @Nullable Object value) {
        commitEach(property, current -> value);
    }

    private void commitEach(PropertyDef property, UnaryOperator<Object> change) {
        List<Edit> edits = new ArrayList<>();
        String name = "Set " + label(property);
        for (Instance target : targets) {
            PropertyDef own = same(target, property);
            if (own == null) continue;
            Object current = SceneDocument.wire(target, own);
            Object next = change.apply(current);
            if (next != null && next.equals(current)) continue;
            edits.add(new SetProperty(document.ref(target.id()), own.index(), next, name));
        }
        if (edits.isEmpty()) return;
        document.history().execute(edits.size() == 1 ? edits.getFirst() : new Batch(name, edits));
    }

    static @Nullable PropertyDef same(Instance target, PropertyDef property) {
        PropertyDef own = target.def().property(property.name());
        return own != null && own.type() == property.type() ? own : null;
    }

    private boolean isMixed(Instance primary, PropertyDef property) {
        if (targets.size() < 2) return false;
        Object value = SceneDocument.wire(primary, property);
        for (Instance target : targets) {
            PropertyDef own = same(target, property);
            if (own != null && !PendingEdits.same(value, SceneDocument.wire(target, own))) return true;
        }
        return false;
    }

    static String label(PropertyDef property) {
        String name = property.name();
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int n = 0; n < name.length(); n++) {
            char c = name.charAt(n);
            if (n == 0) out.append(Character.toUpperCase(c));
            else if (Character.isUpperCase(c)) out.append(' ').append(c);
            else out.append(c);
        }
        return out.toString();
    }

    private void beginLabelled(String label) {
        float start = ImGui.getCursorPosX();
        float available = ImGui.getContentRegionAvailX();
        float column = Math.clamp(available * LABEL_SHARE, EditorScale.of(LABEL_MIN), EditorScale.of(LABEL_MAX));
        ImGui.alignTextToFramePadding();
        ImGui.pushStyleColor(ImGuiCol.Text, mixed ? EditorStyle.COLOR_TEXT_FAINT : EditorStyle.COLOR_TEXT);
        ImGui.textUnformatted(mixed ? label + "  ·  mixed" : label);
        ImGui.popStyleColor();
        if (ImGui.isItemHovered() && (mixed || doc != null)) {
            List<String> tips = new ArrayList<>();
            if (doc != null && !doc.isEmpty()) tips.add(doc);
            if (mixed) tips.add("The selected instances have different values, editing sets them all.");
            ImGui.setTooltip(String.join("\n\n", tips));
        }
        ImGui.sameLine(start + column);
        ImGui.setNextItemWidth(-1.0f);
    }

    private void renderBoolean(Instance instance, PropertyDef property) {
        boolean current = property.getBool(instance);
        beginLabelled(label(property));
        if (Switches.draw("##value", current) != current) commit(instance, property, !current);
    }

    private void renderInt(Instance instance, PropertyDef property) {
        int current = (int) property.getNum(instance);
        int[] value = {current};
        beginLabelled(label(property));
        int min = Double.isFinite(property.min()) ? (int) property.min() : 0;
        int max = Double.isFinite(property.max()) ? (int) property.max() : 0;
        if (ImGui.dragInt("##value", value, 1.0f, min, max) && value[0] != current) commit(instance, property, value[0]);
    }

    private void renderNumber(Instance instance, PropertyDef property) {
        float current = (float) property.getNum(instance);
        beginLabelled(label(property));
        float width = ImGui.getContentRegionAvailX();
        float updated = Double.isFinite(property.min()) && Double.isFinite(property.max())
                ? NumberFields.ranged("##" + instance.id() + ":" + property.index(), current, DRAG_STEP, width, (float) property.min(), (float) property.max())
                : NumberFields.scalar("##" + instance.id() + ":" + property.index(), current, DRAG_STEP, width);
        if (Float.compare(updated, current) != 0) commit(instance, property, property.clamp(updated));
    }

    private void renderString(Instance instance, PropertyDef property, String key) {
        String current = property.getObj(instance) instanceof String text ? text : "";
        ImString buffer = strings.computeIfAbsent(key, ignored -> new ImString(STRING_CAPACITY));
        if (!key.equals(typing)) buffer.set(current);
        beginLabelled(label(property));
        if (ImGui.inputText("##value", buffer) && !buffer.get().equals(current)) commit(instance, property, buffer.get());
        if (property.type() == PropertyType.ASSET && ImGui.beginDragDropTarget()) {
            String asset = ImGui.acceptDragDropPayload(AssetsPanel.PAYLOAD, String.class);
            if (asset != null) commit(instance, property, asset);
            ImGui.endDragDropTarget();
        }
        if (ImGui.isItemActive()) typing = key;
        else if (key.equals(typing)) typing = null;
    }

    private void renderVector3(Instance instance, PropertyDef property) {
        Vector3 current = (Vector3) property.getObj(instance);
        float[] values = {(float) current.x(), (float) current.y(), (float) current.z()};
        beginLabelled(label(property));
        if (NumberFields.vector("##" + instance.id() + ":" + property.index(), values, 3, ImGui.getContentRegionAvailX(), DRAG_STEP)) {
            boolean[] axes = changedAxes(current, values);
            commitEach(property, value -> axisWise((Vector3) value, values, axes));
        }
    }

    private static boolean[] changedAxes(Vector3 current, float[] values) {
        return new boolean[] {values[0] != (float) current.x(), values[1] != (float) current.y(), values[2] != (float) current.z()};
    }

    private static Vector3 axisWise(Vector3 current, float[] values, boolean[] axes) {
        double x = axes[0] ? values[0] : current.x();
        double y = axes[1] ? values[1] : current.y();
        double z = axes[2] ? values[2] : current.z();
        return new Vector3(x, y, z);
    }

    private void renderQuaternion(Instance instance, PropertyDef property, String key) {
        Quat current = (Quat) property.getObj(instance);
        beginLabelled(label(property));
        Quat updated = eulerField("##" + key, current, key);
        if (updated != null) commit(instance, property, updated);
    }

    private void renderCFrame(Instance instance, PropertyDef property, String key) {
        CFrame current = (CFrame) property.getObj(instance);
        Vector3 position = current.position();
        float[] values = {(float) position.x(), (float) position.y(), (float) position.z()};
        beginLabelled("Position");
        if (NumberFields.vector("##position" + key, values, 3, ImGui.getContentRegionAvailX(), DRAG_STEP)) {
            boolean[] axes = changedAxes(position, values);
            commitEach(property, value -> ((CFrame) value).withPosition(axisWise(((CFrame) value).position(), values, axes)));
        }
        beginLabelled("Rotation");
        Quat rotation = eulerField("##rotation" + key, current.rotation(), key);
        if (rotation != null) commitEach(property, value -> ((CFrame) value).withRotation(rotation));
    }

    private @Nullable Quat eulerField(String id, Quat current, String key) {
        Euler euler = eulers.computeIfAbsent(key, ignored -> new Euler());
        boolean editing = ImGui.isAnyItemActive() && ImGui.isWindowFocused();
        if (!editing && (euler.seen == null || differs(euler.seen, current))) {
            Vector3f angles = new Quaternionf((float) current.x(), (float) current.y(), (float) current.z(), (float) current.w())
                    .getEulerAnglesYXZ(new Vector3f()).mul(RADIANS_TO_DEGREES);
            euler.degrees[0] = angles.x;
            euler.degrees[1] = angles.y;
            euler.degrees[2] = angles.z;
            euler.seen = current;
        }
        if (!NumberFields.vector(id, euler.degrees, 3, ImGui.getContentRegionAvailX(), DEGREE_STEP)) return null;
        Quaternionf rotated = new Quaternionf()
                .rotateY(euler.degrees[1] * DEGREES_TO_RADIANS)
                .rotateX(euler.degrees[0] * DEGREES_TO_RADIANS)
                .rotateZ(euler.degrees[2] * DEGREES_TO_RADIANS);
        Quat updated = new Quat(rotated.x, rotated.y, rotated.z, rotated.w);
        euler.seen = updated;
        return updated;
    }

    private static boolean differs(Quat a, Quat b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        return dot < 1.0 - QUAT_EPSILON;
    }

    private void renderColor(Instance instance, PropertyDef property) {
        Color current = (Color) property.getObj(instance);
        float[] values = {current.r(), current.g(), current.b(), current.a()};
        beginLabelled(label(property));
        if (ImGui.colorEdit4("##value", values, COLOR_EDIT_FLAGS)) {
            Color updated = new Color(values[0], values[1], values[2], values[3]);
            if (!updated.equals(current)) commit(instance, property, updated);
        }
    }

    private void renderUDim2(Instance instance, PropertyDef property) {
        UDim2 current = (UDim2) property.getObj(instance);
        String name = label(property);
        String id = "##" + instance.id() + ":" + property.index();
        float[] x = {(float) current.xScale(), (float) current.xOffset()};
        beginLabelled(name + " X");
        udimMenu(instance, property, id + "x");
        boolean changedX = NumberFields.pair(id + "x", x, UDIM_LABELS, UDIM_STEPS, ImGui.getContentRegionAvailX());
        float[] y = {(float) current.yScale(), (float) current.yOffset()};
        beginLabelled(name + " Y");
        udimMenu(instance, property, id + "y");
        boolean changedY = NumberFields.pair(id + "y", y, UDIM_LABELS, UDIM_STEPS, ImGui.getContentRegionAvailX());
        if (changedX || changedY) commit(instance, property, new UDim2(x[0], x[1], y[0], y[1]));
    }

    private void udimMenu(Instance instance, PropertyDef property, String id) {
        if (!ImGui.beginPopupContextItem(id + "-menu")) return;
        if (ImGui.menuItem("Fill parent")) commit(instance, property, new UDim2(1, 0, 1, 0));
        if (ImGui.menuItem("Centre in parent")) commit(instance, property, new UDim2(0.5, 0, 0.5, 0));
        if (ImGui.menuItem("Clear pixel offsets")) {
            UDim2 current = (UDim2) property.getObj(instance);
            commit(instance, property, new UDim2(current.xScale(), 0, current.yScale(), 0));
        }
        ImGui.endPopup();
    }

    private void renderEnum(Instance instance, PropertyDef property) {
        Object current = property.getObj(instance);
        beginLabelled(label(property));
        if (!(current instanceof Enum<?> value)) {
            Texts.muted("none");
            return;
        }
        if (!ImGui.beginCombo("##value", Enums.name(value))) return;
        for (Object candidate : value.getDeclaringClass().getEnumConstants()) {
            Enum<?> option = (Enum<?>) candidate;
            if (ImGui.selectable(Enums.name(option), option == value) && option != value) commit(instance, property, option);
        }
        ImGui.endCombo();
    }

    private void renderRef(Instance instance, PropertyDef property) {
        Object current = property.getObj(instance);
        String shown = current instanceof Instance target ? target.name() : "None";
        beginLabelled(label(property));
        if (ImGui.beginCombo("##value", shown)) {
            if (ImGui.selectable("None", current == null) && current != null) commit(instance, property, null);
            Instance world = document.world();
            if (world != null) {
                int[] left = {REF_CHOICES};
                for (Instance child : world.children()) refChoices(instance, property, current, child, left);
            }
            ImGui.endCombo();
        }
        if (ImGui.beginDragDropTarget()) {
            Integer dropped = ImGui.acceptDragDropPayload(ExplorerPanel.PAYLOAD_INSTANCE, Integer.class);
            if (dropped != null && !Objects.equals(dropped, current instanceof Instance target ? target.id() : null)) {
                commit(instance, property, dropped);
            }
            ImGui.endDragDropTarget();
        }
    }

    private void refChoices(Instance owner, PropertyDef property, @Nullable Object current, Instance candidate, int[] left) {
        if (left[0] <= 0 || !document.editable(candidate)) return;
        left[0]--;
        ImGui.pushID(candidate.id());
        String text = String.format(Locale.ROOT, "%s  (%s)", candidate.name(), candidate.def().name());
        if (ImGui.selectable(text, candidate == current) && candidate != current) commit(owner, property, candidate.id());
        ImGui.popID();
        List<Instance> children = candidate.children();
        for (Instance child : children) refChoices(owner, property, current, child, left);
    }
}
