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
import com.meekdev.moud.mod.client.editor.assets.AssetEntry;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.assets.AssetKind;
import com.meekdev.moud.mod.client.editor.assets.AssetScanner;
import com.meekdev.moud.mod.client.editor.assets.AssetsPanel;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.PendingEdits;
import com.meekdev.moud.mod.client.editor.document.ReferencePick;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.kit.NumberFields;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Switches;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    static final float DRAG_STEP = 0.05f;
    private static final float NUDGE_FAST = 10.0f;
    private static final float NUDGE_FINE = 0.1f;
    private static final float LABEL_SHARE = 0.36f;
    private static final float LABEL_MIN = 84.0f;
    private static final float LABEL_MAX = 150.0f;
    static final float DEGREE_STEP = 0.5f;
    static final float RADIANS_TO_DEGREES = 57.295776f;
    static final float DEGREES_TO_RADIANS = 0.017453293f;
    private static final float QUAT_EPSILON = 0.0001f;
    private static final int STRING_CAPACITY = 512;
    private static final int REF_CHOICES = 400;
    static final String[] UDIM_LABELS = {"scale", "px"};
    static final float[] UDIM_STEPS = {0.01f, 1.0f};

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
    private @Nullable Instance rowInstance;
    private @Nullable String labelOverride;
    private @Nullable PropertyDef rowProperty;
    private float nudgeRemainder;
    private @Nullable String colourKey;
    private final float[] colourBuffer = new float[4];
    private final ImString hexInput = new ImString(16);
    private boolean colourOpen;
    private final ImString assetSearch = new ImString(128);
    private static @Nullable Object copiedValue;
    private static @Nullable PropertyType copiedType;
    private static final Deque<Color> RECENT = new ArrayDeque<>();
    private static final int RECENT_KEEP = 12;

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
        render(instance, property, selected, null);
    }

    void render(Instance instance, PropertyDef property, List<Instance> selected, @Nullable String labelOverride) {
        this.labelOverride = labelOverride;
        targets = selected;
        mixed = isMixed(instance, property);
        doc = PropertyDocs.of(instance.def(), property.name());
        String key = instance.id() + ":" + property.index();
        seen.add(key);
        rowInstance = instance;
        rowProperty = property;
        ImGui.pushID(property.name());
        boolean locked = property.driven() || property.readOnly();
        ImGui.beginDisabled(locked);
        switch (property.type()) {
            case BOOL -> renderBoolean(instance, property);
            case INT -> renderInt(instance, property);
            case NUM -> renderNumber(instance, property);
            case STRING, ASSET -> renderString(instance, property, key);
            case VEC3 -> renderVector3(instance, property);
            case QUAT -> renderQuaternion(instance, property, key);
            case CFRAME -> renderCFrame(instance, property, key);
            case COLOR -> renderColor(instance, property, key);
            case UDIM2 -> renderUDim2(instance, property);
            case ENUM -> renderEnum(instance, property);
            case REF -> renderRef(instance, property);
        }
        ImGui.endDisabled();
        if (locked && ImGui.isItemHovered()) ImGui.setTooltip(lockedReason(property));
        ImGui.popID();
    }

    private static String lockedReason(PropertyDef property) {
        if (!property.readOnly()) return "Driven by the engine";
        return "Worked out while the interface is laid out";
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

    private String label(PropertyDef property) {
        return labelOverride != null && property.name().equals("value") ? labelOverride : PropertyRows.labelOf(property);
    }

    static String labelOf(PropertyDef property) {
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

    static float labelColumn(float available) {
        return Math.clamp(available * LABEL_SHARE, EditorScale.of(LABEL_MIN), EditorScale.of(LABEL_MAX));
    }

    private void beginLabelled(String label) {
        float start = ImGui.getCursorPosX();
        float available = ImGui.getContentRegionAvailX();
        float column = labelColumn(available);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = ImGui.getFrameHeight();
        ImGui.invisibleButton("##label-" + label, Math.max(1.0f, column - EditorStyle.itemSpacingX()), height);
        boolean over = ImGui.isItemHovered();
        boolean numeric = rowProperty != null && !rowProperty.driven()
                && (rowProperty.type() == PropertyType.NUM || rowProperty.type() == PropertyType.INT);
        if (numeric && (over || ImGui.isItemActive())) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        if (numeric && ImGui.isItemActive()) nudge(ImGui.getIO().getMouseDeltaX());
        else if (numeric) nudgeRemainder = 0;
        int colour = labelColour(over);
        String shown = mixed ? label + "  ·  mixed" : label;
        float textY = top + (height - ImGui.getTextLineHeight()) * 0.5f;
        ImGui.getWindowDrawList().addText(left, textY, colour, shown);
        String unit = rowProperty == null ? null : unit(rowProperty, label);
        if (unit != null) {
            float after = left + ImGui.calcTextSize(shown).x + EditorScale.of(4);
            ImGui.getWindowDrawList().addText(after, textY, EditorStyle.COLOR_TEXT_FAINT, unit);
        }
        if (over && !ImGui.isItemActive()) {
            List<String> tips = new ArrayList<>();
            if (doc != null && !doc.isEmpty()) tips.add(doc);
            if (mixed) tips.add("The selected instances have different values, editing sets them all.");
            if (numeric) tips.add("Drag the name sideways to change the value. Shift is faster, Alt is finer.");
            tips.add("Right-click for reset, copy and paste.");
            ImGui.setTooltip(String.join("\n\n", tips));
        }
        if (rowProperty != null && rowInstance != null && ImGui.beginPopupContextItem("##row-menu-" + label)) {
            rowMenu(rowInstance, rowProperty);
            ImGui.endPopup();
        }
        ImGui.sameLine(start + column);
        ImGui.setNextItemWidth(-1.0f);
    }

    private int labelColour(boolean over) {
        if (mixed) return EditorStyle.COLOR_TEXT_FAINT;
        if (over) return EditorStyle.COLOR_TEXT_FOCUS;
        return EditorStyle.COLOR_TEXT;
    }

    private static float nudgeSpeed() {
        if (ImGui.getIO().getKeyShift()) return NUDGE_FAST;
        if (ImGui.getIO().getKeyAlt()) return NUDGE_FINE;
        return 1.0f;
    }

    private void nudge(float mouseDelta) {
        if (mouseDelta == 0 || rowInstance == null || rowProperty == null) return;
        float speed = nudgeSpeed();
        if (rowProperty.type() == PropertyType.INT) {
            nudgeRemainder += mouseDelta * 0.1f * speed;
            int whole = (int) nudgeRemainder;
            if (whole == 0) return;
            nudgeRemainder -= whole;
            commit(rowInstance, rowProperty, (int) rowProperty.clamp(rowProperty.getNum(rowInstance) + whole));
            return;
        }
        commit(rowInstance, rowProperty, rowProperty.clamp(rowProperty.getNum(rowInstance) + mouseDelta * DRAG_STEP * speed));
    }

    private void rowMenu(Instance instance, PropertyDef property) {
        Texts.muted(label(property));
        ImGui.separator();
        if (ImGui.menuItem("Reset to default")) commit(instance, property, defaultWire(property));
        if (ImGui.menuItem("Copy value")) {
            copiedValue = SceneDocument.wire(instance, property);
            copiedType = property.type();
        }
        boolean fits = copiedType == property.type();
        if (ImGui.menuItem("Paste value", "", false, fits)) commit(instance, property, copiedValue);
        if (property.type() == PropertyType.UDIM2) {
            ImGui.separator();
            if (ImGui.menuItem("Fill parent")) commit(instance, property, new UDim2(1, 0, 1, 0));
            if (ImGui.menuItem("Centre in parent")) commit(instance, property, new UDim2(0.5, 0, 0.5, 0));
            if (ImGui.menuItem("Clear pixel offsets")) {
                UDim2 current = (UDim2) property.getObj(instance);
                commit(instance, property, new UDim2(current.xScale(), 0, current.yScale(), 0));
            }
        }
        if (property.type() == PropertyType.CFRAME || property.type() == PropertyType.QUAT) {
            ImGui.separator();
            if (ImGui.menuItem("Clear rotation")) commitEach(property, value -> withTurn(value, Quat.IDENTITY));
            if (ImGui.menuItem("Snap rotation to 90°")) commitEach(property, value -> withTurn(value, snapped(turnOf(value))));
            if (ImGui.beginMenu("Face")) {
                String[] names = {"North", "East", "South", "West"};
                for (int n = 0; n < 4; n++) {
                    Quat facing = Quat.axisAngle(new Vector3(0, 1, 0), Math.toRadians(-90.0 * n));
                    if (ImGui.menuItem(names[n])) commitEach(property, value -> withTurn(value, facing));
                }
                ImGui.endMenu();
            }
        }
    }

    private static Object withTurn(Object value, Quat turn) {
        return value instanceof CFrame frame ? frame.withRotation(turn) : turn;
    }

    private static Quat turnOf(Object value) {
        return value instanceof CFrame frame ? frame.rotation() : (Quat) value;
    }

    private static Quat snapped(Quat q) {
        Vector3f angles = new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()).getEulerAnglesYXZ(new Vector3f());
        float quarter = (float) (Math.PI / 2);
        Quaternionf out = new Quaternionf()
                .rotateY(Math.round(angles.y / quarter) * quarter)
                .rotateX(Math.round(angles.x / quarter) * quarter)
                .rotateZ(Math.round(angles.z / quarter) * quarter);
        return new Quat(out.x, out.y, out.z, out.w);
    }

    private static @Nullable Object defaultWire(PropertyDef property) {
        Object value = property.defaultValue();
        if (property.type() == PropertyType.REF) return null;
        if (property.type() == PropertyType.INT && value instanceof Number n) return n.intValue();
        if (property.type() == PropertyType.NUM && value instanceof Number n) return n.doubleValue();
        return value;
    }

    private static @Nullable String unit(PropertyDef property, String label) {
        String name = property.name().toLowerCase(Locale.ROOT);
        if (label.equals("Rotation") || name.contains("angle") || name.equals("fov") || name.contains("yaw")) return "°";
        if (label.equals("Position") || name.equals("size") || name.contains("distance") || name.equals("range")
                || name.equals("offset") || name.equals("radius") || name.equals("height") || name.equals("width")) return "m";
        if (name.contains("transparency")) return "0–1";
        if (name.equals("volume")) return "×";
        if (name.equals("pixelspermetre")) return "px/m";
        if (name.endsWith("time") || name.equals("cooldown") || name.equals("delay") || name.equals("fadein")) return "s";
        return null;
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
        boolean asset = property.type() == PropertyType.ASSET;
        float buttons = asset ? ImGui.getFrameHeight() * 2 + EditorStyle.itemSpacingX() * 2 : 0;
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - buttons);
        String hint = asset ? "drop an asset or browse" : "";
        if (ImGui.inputTextWithHint("##value", hint, buffer) && !buffer.get().equals(current)) commit(instance, property, buffer.get());
        if (ImGui.isItemActive()) typing = key;
        else if (key.equals(typing)) typing = null;
        if (!asset) return;
        if (ImGui.beginDragDropTarget()) {
            String dropped = ImGui.acceptDragDropPayload(AssetsPanel.PAYLOAD, String.class);
            if (dropped != null) commit(instance, property, dropped);
            ImGui.endDragDropTarget();
        }
        ImGui.sameLine();
        if (ImGui.button("...##browse", ImGui.getFrameHeight(), ImGui.getFrameHeight())) {
            assetSearch.set("");
            ImGui.openPopup("##asset-browse");
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Browse the place's files");
        ImGui.sameLine();
        ImGui.beginDisabled(current.isEmpty());
        if (ImGui.button("x##clear", ImGui.getFrameHeight(), ImGui.getFrameHeight())) commit(instance, property, "");
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Clear");
        if (!ImGui.beginPopup("##asset-browse")) return;
        AssetKind wanted = kindFor(property);
        Texts.muted(wanted == null ? "Any file" : wanted.label());
        ImGui.setNextItemWidth(EditorScale.of(260));
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.inputTextWithHint("##asset-search", "Search", assetSearch);
        ImGui.beginChild("##asset-list", EditorScale.of(320), EditorScale.of(260), true);
        int shown = 0;
        for (AssetEntry entry : AssetScanner.search(AssetFiles.root(), assetSearch.get().strip())) {
            if (entry.folder() || (wanted != null && entry.kind() != wanted)) continue;
            String res = AssetFiles.res(entry.path());
            if (res == null) continue;
            if (ImGui.selectable(entry.name() + "##" + res, res.equals(current))) {
                commit(instance, property, res);
                ImGui.closeCurrentPopup();
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(res);
            if (++shown >= 300) break;
        }
        if (shown == 0) Texts.muted("Nothing here matches");
        ImGui.endChild();
        ImGui.endPopup();
    }

    private static @Nullable AssetKind kindFor(PropertyDef property) {
        String name = property.name().toLowerCase(Locale.ROOT);
        if (name.contains("mesh")) return AssetKind.MODEL;
        if (name.contains("sound")) return AssetKind.SOUND;
        if (name.contains("image") || name.contains("skin") || name.contains("lut") || name.contains("texture")) return AssetKind.TEXTURE;
        if (name.contains("font")) return AssetKind.FONT;
        if (name.contains("shader")) return AssetKind.SHADER;
        if (name.equals("source")) return AssetKind.SCRIPT;
        return null;
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

    private void renderColor(Instance instance, PropertyDef property, String key) {
        Color current = (Color) property.getObj(instance);
        beginLabelled(label(property));
        float height = ImGui.getFrameHeight();
        float swatch = Math.min(EditorScale.of(56), ImGui.getContentRegionAvailX());
        if (ImGui.colorButton("##swatch", new float[] {current.r(), current.g(), current.b(), current.a()},
                ImGuiColorEditFlags.AlphaPreviewHalf, swatch, height)) {
            colourKey = key;
            colourBuffer[0] = current.r();
            colourBuffer[1] = current.g();
            colourBuffer[2] = current.b();
            colourBuffer[3] = current.a();
            hexInput.set(hex(current));
            ImGui.openPopup("##colour-popup");
        }
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        Texts.muted(hex(current));
        if (!key.equals(colourKey)) return;
        if (!ImGui.beginPopup("##colour-popup")) {
            if (colourOpen) remember(new Color(colourBuffer[0], colourBuffer[1], colourBuffer[2], colourBuffer[3]));
            colourOpen = false;
            return;
        }
        colourOpen = true;
        ImGui.setNextItemWidth(EditorScale.of(220));
        if (ImGui.colorPicker4("##picker", colourBuffer, ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.PickerHueWheel
                | ImGuiColorEditFlags.NoSidePreview | ImGuiColorEditFlags.NoInputs)) {
            Color chosen = new Color(colourBuffer[0], colourBuffer[1], colourBuffer[2], colourBuffer[3]);
            hexInput.set(hex(chosen));
            commit(instance, property, chosen);
        }
        ImGui.setNextItemWidth(EditorScale.of(120));
        if (ImGui.inputText("Hex##hex", hexInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
            Color parsed = parseHex(hexInput.get());
            if (parsed != null) {
                colourBuffer[0] = parsed.r();
                colourBuffer[1] = parsed.g();
                colourBuffer[2] = parsed.b();
                colourBuffer[3] = parsed.a();
                commit(instance, property, parsed);
            }
        }
        swatches("Recent", new ArrayList<>(RECENT), instance, property);
        swatches("In this place", palette(), instance, property);
        ImGui.endPopup();
    }

    private void swatches(String title, List<Color> colours, Instance instance, PropertyDef property) {
        if (colours.isEmpty()) return;
        Texts.muted(title);
        float size = EditorScale.of(18);
        for (int n = 0; n < colours.size(); n++) {
            Color c = colours.get(n);
            if (n % 8 != 0) ImGui.sameLine();
            if (ImGui.colorButton("##" + title + n, new float[] {c.r(), c.g(), c.b(), c.a()}, ImGuiColorEditFlags.AlphaPreviewHalf, size, size)) {
                colourBuffer[0] = c.r();
                colourBuffer[1] = c.g();
                colourBuffer[2] = c.b();
                colourBuffer[3] = c.a();
                hexInput.set(hex(c));
                commit(instance, property, c);
            }
        }
    }

    private List<Color> palette() {
        List<Color> found = new ArrayList<>();
        Instance world = document.world();
        if (world != null) collectColours(world, found);
        return found;
    }

    private void collectColours(Instance at, List<Color> found) {
        for (Instance child : at.children()) {
            if (found.size() >= 16) return;
            for (PropertyDef property : child.def().properties()) {
                if (property.type() != PropertyType.COLOR || !(property.getObj(child) instanceof Color c)) continue;
                if (found.stream().noneMatch(known -> hex(known).equals(hex(c)))) found.add(c);
                if (found.size() >= 16) return;
            }
            collectColours(child, found);
        }
    }

    private static void remember(Color colour) {
        RECENT.removeIf(known -> hex(known).equals(hex(colour)));
        RECENT.addFirst(colour);
        while (RECENT.size() > RECENT_KEEP) RECENT.removeLast();
    }

    private static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", Math.round(c.r() * 255), Math.round(c.g() * 255), Math.round(c.b() * 255), Math.round(c.a() * 255));
    }

    private static @Nullable Color parseHex(String text) {
        String digits = text.strip().replace("#", "");
        if (digits.length() != 6 && digits.length() != 8) return null;
        try {
            long value = Long.parseLong(digits, 16);
            if (digits.length() == 6) value = value << 8 | 0xFF;
            return new Color(((value >> 24) & 0xFF) / 255f, ((value >> 16) & 0xFF) / 255f, ((value >> 8) & 0xFF) / 255f, (value & 0xFF) / 255f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void renderUDim2(Instance instance, PropertyDef property) {
        UDim2 current = (UDim2) property.getObj(instance);
        String name = label(property);
        String id = "##" + instance.id() + ":" + property.index();
        float[] x = {(float) current.xScale(), (float) current.xOffset()};
        beginLabelled(name + " X");
        boolean changedX = NumberFields.pair(id + "x", x, UDIM_LABELS, UDIM_STEPS, ImGui.getContentRegionAvailX());
        float[] y = {(float) current.yScale(), (float) current.yOffset()};
        beginLabelled(name + " Y");
        boolean changedY = NumberFields.pair(id + "y", y, UDIM_LABELS, UDIM_STEPS, ImGui.getContentRegionAvailX());
        if (changedX || changedY) commit(instance, property, new UDim2(x[0], x[1], y[0], y[1]));
    }

    private void renderEnum(Instance instance, PropertyDef property) {
        Object current = property.getObj(instance);
        beginLabelled(label(property));
        if (!(current instanceof Enum<?> value)) {
            Texts.muted("none");
            return;
        }
        Object[] options = value.getDeclaringClass().getEnumConstants();
        if (options.length <= 4) {
            List<String> labels = new ArrayList<>();
            for (Object option : options) labels.add(Enums.name((Enum<?>) option));
            if (SegmentedControl.width(labels) <= ImGui.getContentRegionAvailX()) {
                int chosen = SegmentedControl.render("##segments", labels, value.ordinal());
                if (chosen != value.ordinal()) commit(instance, property, options[chosen]);
                return;
            }
        }
        if (!ImGui.beginCombo("##value", Enums.name(value))) return;
        for (Object candidate : options) {
            Enum<?> option = (Enum<?>) candidate;
            if (ImGui.selectable(Enums.name(option), option == value) && option != value) commit(instance, property, option);
        }
        ImGui.endCombo();
    }

    private void renderRef(Instance instance, PropertyDef property) {
        Object current = property.getObj(instance);
        String shown = current instanceof Instance target ? target.name() : "None";
        beginLabelled(label(property));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - ImGui.getFrameHeight() * 1.6f - EditorStyle.itemSpacingX());
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
        ImGui.sameLine();
        boolean picking = ReferencePick.active() && ReferencePick.what().equals(instance.id() + ":" + property.name());
        if (ImGui.button((picking ? "..." : "Pick") + "##eyedropper", ImGui.getFrameHeight() * 1.6f, ImGui.getFrameHeight())) {
            if (picking) {
                ReferencePick.cancel();
            } else {
                ReferencePick.begin(instance.id() + ":" + property.name(), picked -> {
                    if (document.editable(picked)) commit(instance, property, picked.id());
                });
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(picking ? "Click something in the viewport, Escape to stop" : "Pick the target by clicking it in the viewport");
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
