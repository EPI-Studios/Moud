package com.moud.client.fabric.editor.panels;

import com.miry.platform.InputConstants;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.moud.client.fabric.render.MoudIcons;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.DraggableNumberField;
import com.miry.ui.widgets.TextField;
import com.miry.graphics.Texture;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.material.MoudMaterial;
import com.moud.client.fabric.render.material.MoudMaterialParser;
import com.moud.client.fabric.render.material.MoudMaterialWriter;
import com.moud.client.fabric.render.material.MoudShaderFile;
import com.moud.client.fabric.render.material.MoudShaderUniform;
import com.moud.client.fabric.render.material.MoudShaderParser;
import com.moud.client.fabric.util.ParseUtils;
import com.moud.client.fabric.render.preview.MaterialPreviewRenderer;
import com.moud.core.PropertyDef;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.session.Session;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class MaterialEditor {
    private static final long DRAG_MATERIAL_SEND_INTERVAL_MS = 100;
    private static final int MATERIAL_PARAM_ROW_ESTIMATE = 16;

    private final EditorRuntime runtime;

    private final Map<String, TextField> materialStringFields = new HashMap<>();
    private final Map<String, MaterialStringBinding> materialStringBindings = new HashMap<>();
    private final Map<String, DraggableNumberField> materialNumberFields = new HashMap<>();
    private final Map<String, Float> pendingMaterialNumbers = new HashMap<>();
    private final Map<String, Float> lastSentMaterialNumbers = new HashMap<>();
    private final Map<String, Long> lastSentMaterialAtMs = new HashMap<>();
    private final Map<String, MaterialNumberBinding> materialNumberBindings = new HashMap<>();
    private boolean syncingMaterialNumbers;

    private final ContextMenu materialTextureMenu = new ContextMenu();
    private String materialTextureMenuMaterialPath;
    private String materialTextureMenuUniform;

    MaterialEditor(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    boolean handleKey(UiContext ctx, KeyEvent e, Clipboard clipboard) {
        if (ctx == null || e == null) {
            return false;
        }
        for (var entry : materialStringFields.entrySet()) {
            TextField tf = entry.getValue();
            if (tf != null && tf.isFocused(ctx)) {
                tf.handleKey(e, clipboard);
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    commitMaterialStringField(entry.getKey(), tf.text());
                }
                return true;
            }
        }
        for (DraggableNumberField nf : materialNumberFields.values()) {
            if (nf != null && nf.handleKey(ctx, e, clipboard)) {
                return true;
            }
        }
        return false;
    }

    boolean handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return false;
        }
        for (TextField tf : materialStringFields.values()) {
            if (tf != null && tf.isFocused(ctx)) {
                tf.handleTextInput(e);
                return true;
            }
        }
        for (DraggableNumberField nf : materialNumberFields.values()) {
            if (nf != null && nf.handleTextInput(ctx, e)) {
                return true;
            }
        }
        return false;
    }

    void onSelectionChanged() {
        materialStringFields.clear();
        materialStringBindings.clear();
        materialNumberFields.clear();
        materialNumberBindings.clear();
        pendingMaterialNumbers.clear();
        lastSentMaterialNumbers.clear();
        lastSentMaterialAtMs.clear();
        materialTextureMenu.close();
        materialTextureMenuMaterialPath = null;
        materialTextureMenuUniform = null;
    }

    int estimateMaterialParamRows(List<PropertyDef> props, Map<String, String> values) {
        if (props == null || props.isEmpty() || values == null || values.isEmpty()) {
            return 0;
        }
        int rows = 0;
        for (PropertyDef prop : props) {
            if (!InspectorPanel.isAssetKind(prop, "material")) {
                continue;
            }
            String v = values.get(prop.key());
            if (v == null) {
                v = prop.defaultValue();
            }
            if (v == null || v.isBlank()) {
                continue;
            }
            rows += MATERIAL_PARAM_ROW_ESTIMATE;
        }
        return rows;
    }

    int renderMaterialShaderParams(InspectorPanel panel,
                                   Ui ui,
                                   UiRenderer r,
                                   UiContext uiContext,
                                   Theme theme,
                                   List<PropertyDef> props,
                                   Map<String, String> values,
                                   String filterLower,
                                   int x,
                                   int y,
                                   int w,
                                   int rowH,
                                   int labelW,
                                   boolean interactive) {
        if (props == null || props.isEmpty() || values == null || values.isEmpty()) {
            return y;
        }

        HashSet<String> usedTitles = new HashSet<>();
        for (PropertyDef prop : props) {
            if (prop == null || prop.key() == null || prop.key().isBlank()) {
                continue;
            }
            if (!InspectorPanel.isAssetKind(prop, "material")) {
                continue;
            }
            String v = values.get(prop.key());
            if (v == null) {
                v = prop.defaultValue();
            }
            if (v == null) {
                continue;
            }
            String materialPath = v.trim();
            if (materialPath.isEmpty()) {
                continue;
            }

            String base = prop.category() == null || prop.category().isBlank() ? "Material" : prop.category().trim();
            String title = base + " Shader Parameters";
            if (!usedTitles.add(title)) {
                title = title + " (" + prop.key() + ")";
                usedTitles.add(title);
            }

            y = panel.renderGroupHeader(ui, r, theme, x, y, w, title);
            if (!panel.isExpanded(title)) {
                continue;
            }
            y = renderMaterialShaderParamsGroup(ui, r, uiContext, theme, materialPath, filterLower, x, y, w, rowH, labelW, interactive);
        }

        return y;
    }

    void flushPendingMaterialUploads(UiContext uiContext) {
        if (pendingMaterialNumbers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        ArrayList<String> keys = new ArrayList<>(pendingMaterialNumbers.keySet());
        HashMap<String, ArrayList<MaterialEdit>> editsByMaterial = new HashMap<>();

        for (String key : keys) {
            MaterialNumberBinding binding = materialNumberBindings.get(key);
            if (binding == null) {
                pendingMaterialNumbers.remove(key);
                continue;
            }

            DraggableNumberField nf = materialNumberFields.get(key);
            boolean captured = nf != null && uiContext != null && uiContext.pointer().isCaptured(nf.id());
            if (captured) {
                long last = lastSentMaterialAtMs.getOrDefault(key, 0L);
                if ((now - last) < DRAG_MATERIAL_SEND_INTERVAL_MS) {
                    continue;
                }
            }

            float v = pendingMaterialNumbers.get(key);
            float cmp = uniformIsInt(binding.glslType) ? Math.round(v) : v;
            Float last = lastSentMaterialNumbers.get(key);
            if (last != null && Math.abs(last - cmp) < 1e-6f) {
                pendingMaterialNumbers.remove(key);
                continue;
            }

            lastSentMaterialNumbers.put(key, cmp);
            lastSentMaterialAtMs.put(key, now);
            pendingMaterialNumbers.remove(key);

            editsByMaterial.computeIfAbsent(binding.materialPath, ignored -> new ArrayList<>())
                    .add(new MaterialEdit(binding, v));
        }

        for (Map.Entry<String, ArrayList<MaterialEdit>> e : editsByMaterial.entrySet()) {
            String materialPath = e.getKey();
            ArrayList<MaterialEdit> edits = e.getValue();
            if (materialPath == null || materialPath.isBlank() || edits == null || edits.isEmpty()) {
                continue;
            }
            applyMaterialEdits(materialPath, edits);
        }
    }

    void renderMaterialTextureMenu(Ui ui, UiRenderer r, Theme theme, boolean interactive) {
        if (ui == null || r == null || theme == null) {
            return;
        }
        if (!materialTextureMenu.isOpen()) {
            return;
        }
        var input = interactive ? ui.input() : null;
        int itemH = theme.design.menu_item_height;
        if (input != null) {
            materialTextureMenu.updateFromInput(input, theme, itemH);
            EditorUiUtil.clampOpenMenuToScreen(materialTextureMenu, runtime);
        }
        materialTextureMenu.render(r, theme, itemH,
                Theme.toArgb(theme.panelBg),
                Theme.toArgb(theme.widgetHover),
                Theme.toArgb(theme.text),
                materialTextureMenu.hoverIndex());

        if (!interactive || input == null || !input.mousePressed()) {
            return;
        }
        materialTextureMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemH);
    }

    private int renderMaterialShaderParamsGroup(Ui ui,
                                               UiRenderer r,
                                               UiContext uiContext,
                                               Theme theme,
                                               String materialPath,
                                               String filterLower,
                                               int x,
                                               int y,
                                               int w,
                                               int rowH,
                                               int labelW,
                                               boolean interactive) {
        int muted = Theme.toArgb(theme.textMuted);

        if (materialPath == null || materialPath.isBlank() || !materialPath.startsWith(ResPath.SCHEME)) {
            r.drawText("(material must be res://...)", x, r.baselineForBox(y, rowH), muted);
            return y + rowH;
        }

        String materialText = MoudTextAssets.readText(materialPath);
        if (materialText == null) {
            r.drawText("(loading material...)", x, r.baselineForBox(y, rowH), muted);
            return y + rowH;
        }

        MoudMaterial material = MoudMaterialParser.parse(materialText);
        if (material == null) {
            r.drawText("(invalid .moudmat)", x, r.baselineForBox(y, rowH), Theme.toArgb(theme.danger));
            return y + rowH;
        }

        String shaderPath = material.shader() == null ? "" : material.shader().trim();
        if (shaderPath.isEmpty() || !shaderPath.startsWith(ResPath.SCHEME)) {
            r.drawText("(shader must be res://...)", x, r.baselineForBox(y, rowH), Theme.toArgb(theme.danger));
            return y + rowH;
        }

        String shaderText = MoudTextAssets.readText(shaderPath);
        if (shaderText == null) {
            r.drawText("(loading shader...)", x, r.baselineForBox(y, rowH), muted);
            return y + rowH;
        }

        MoudShaderFile shaderFile = MoudShaderParser.parse(shaderText, shaderPath);
        if (shaderFile == null) {
            r.drawText("(invalid .moudshader)", x, r.baselineForBox(y, rowH), Theme.toArgb(theme.danger));
            return y + rowH;
        }

        y = renderMaterialPreviewRow(r, theme, materialPath, x, y, w, rowH, labelW);

        List<MoudShaderUniform> uniforms = shaderFile.exposedUniforms();
        if (uniforms == null || uniforms.isEmpty()) {
            r.drawText("(no @expose uniforms)", x, r.baselineForBox(y, rowH), muted);
            return y + rowH;
        }

        Map<String, MoudMaterial.Param> params = material.params() == null ? Map.of() : material.params();
        for (MoudShaderUniform u : uniforms) {
            if (u == null || u.name() == null || u.name().isBlank()) {
                continue;
            }
            if (!filterLower.isEmpty()) {
                String hay = (u.uiLabel() + " " + u.name()).toLowerCase(Locale.ROOT);
                if (!hay.contains(filterLower) && !filterLower.contains("shader") && !filterLower.contains("param")) {
                    continue;
                }
            }
            MoudMaterial.Param current = params.get(u.name());
            y = renderMaterialUniformRow(ui, r, uiContext, theme, materialPath, shaderPath, u, current, x, y, w, rowH, labelW, interactive);
        }

        return y;
    }

    private int renderMaterialPreviewRow(UiRenderer r,
                                         Theme theme,
                                         String materialPath,
                                         int x,
                                         int y,
                                         int w,
                                         int rowH,
                                         int labelW) {
        int muted = Theme.toArgb(theme.textMuted);
        int valueX = x + labelW + theme.design.space_sm;
        int valueW = Math.max(1, w - (valueX - x));

        int box = Math.max(64, rowH * 4);
        box = Math.min(box, valueW);
        box = Math.max(32, box);
        int boxY = y + 2;

        r.drawText("Preview", x, r.baselineForBox(y, rowH), muted);

        int bg = Theme.toArgb(theme.widgetBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        r.drawRoundedRect(valueX, boxY, box, box, theme.design.radius_sm, bg, theme.design.border_thin, outline);

        MaterialPreviewRenderer.request(materialPath);
        Texture tex = MaterialPreviewRenderer.previewTexture(materialPath);
        if (tex != null) {
            r.drawTexturedRect(tex, valueX, boxY, box, box, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
        } else {
            r.drawText("(loading preview...)", valueX + 6, r.baselineForBox(boxY, rowH), Theme.mulAlpha(muted, 0.75f));
        }

        return y + box + theme.design.space_sm;
    }

    private int renderMaterialUniformRow(Ui ui,
                                         UiRenderer r,
                                         UiContext uiContext,
                                         Theme theme,
                                         String materialPath,
                                         String shaderPath,
                                         MoudShaderUniform uniform,
                                         MoudMaterial.Param current,
                                         int x,
                                         int y,
                                         int w,
                                         int rowH,
                                         int labelW,
                                         boolean interactive) {
        String name = uniform.name();
        String type = uniform.glslType() == null ? "" : uniform.glslType().trim().toLowerCase(Locale.ROOT);
        int valueX = x + labelW + theme.design.space_sm;
        int valueW = Math.max(1, w - (valueX - x));

        r.drawText(uniform.uiLabel(), x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        if (uniform.isSampler()) {
            String tex = "";
            if (current instanceof MoudMaterial.Param.Texture t) {
                tex = t.textureRef();
            } else if (current instanceof MoudMaterial.Param.StringParam s) {
                tex = s.value();
            }
            tex = tex == null ? "" : tex;

            int previewSize = rowH - 4;
            int iconBtnW = Math.max(18, rowH - 4);
            int iconBtnH = rowH - 4;
            int iconGap = theme.design.space_xs;

            int previewX = valueX;
            int previewY = y + 2;
            int fieldX = previewX + previewSize + iconGap;
            int fieldW = Math.max(1, valueW - previewSize - iconGap - iconBtnW - iconGap);

            int previewBg = Theme.toArgb(theme.widgetBg);
            int previewOutline = Theme.toArgb(theme.widgetOutline);
            r.drawRoundedRect(previewX, previewY, previewSize, previewSize, theme.design.radius_sm, previewBg, theme.design.border_thin, previewOutline);
            if (!tex.isBlank()) {
                Identifier texId = MoudTextures.resolve(tex);
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getTextureManager() != null) {
                    AbstractTexture mcTex = mc.getTextureManager().getOrDefault(texId, null);
                    if (mcTex != null) {
                        Texture mTex = Texture.wrapExternal(mcTex.getGlId(), previewSize, previewSize, false);
                        r.drawTexturedRect(mTex, previewX, previewY, previewSize, previewSize, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
                    }
                }
            }

            String fieldKey = "mat:" + materialPath + ":" + name;
            materialStringBindings.putIfAbsent(fieldKey, new MaterialStringBinding(materialPath, name));

            TextField tf = materialStringFields.computeIfAbsent(fieldKey, ignored -> new TextField());
            if (uiContext == null || !tf.isFocused(uiContext)) {
                tf.setText(tex);
                tf.setCursorPos(tf.text().length());
            }
            var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
            tf.render(r, uiContext, input, theme, fieldX, y + 2, fieldW, rowH - 4, true);

            int btnX = fieldX + fieldW + iconGap;
            int btnY = y + 2;
            int menuX = btnX;
            int menuY = btnY + iconBtnH;
            EditorUiUtil.iconButtonOutlined(ui, r, theme, btnX, btnY, iconBtnW, iconBtnH, Icon.IMAGE, input != null, () -> toggleMaterialTextureMenu(menuX, menuY, materialPath, name));
            return y + rowH;
        }

        if ("bool".equals(type) || type.startsWith("bvec")) {
            boolean b = false;
            if (current instanceof MoudMaterial.Param.Bool pb) {
                b = pb.value();
            } else if (current instanceof MoudMaterial.Param.Number num) {
                b = Math.abs(num.value()) > 1e-6f;
            }
            InspectorPanel.renderBool(ui, r, theme, valueX, y, valueW, rowH, b, interactive, next -> commitMaterialBool(materialPath, shaderPath, name, next));
            return y + rowH;
        }

        int comps = uniformComponentCount(type);
        if (comps > 1) {
            float[] vec = readMaterialVec(current, comps);
            int gap = theme.design.space_xs;
            int eachW = Math.max(1, (valueW - gap * (comps - 1)) / comps);
            int fieldH = rowH - 4;
            int fy = y + 2;
            for (int i = 0; i < comps; i++) {
                int fx = valueX + i * (eachW + gap);
                int fw = (i == comps - 1) ? (valueX + valueW - fx) : eachW;
                char prefix = (char) ('x' + i);
                String fieldKey = "mat:" + materialPath + ":" + name + ":" + i;
                materialNumberBindings.putIfAbsent(fieldKey, new MaterialNumberBinding(materialPath, shaderPath, name, type, i, comps));
                renderMaterialPrefixedNumber(ui, r, uiContext, theme, fieldKey, uniform, vec[i], fx, fy, fw, fieldH, String.valueOf(prefix));
            }
            return y + rowH;
        }

        float scalar = readMaterialScalar(current);
        String fieldKey = "mat:" + materialPath + ":" + name;
        materialNumberBindings.putIfAbsent(fieldKey, new MaterialNumberBinding(materialPath, shaderPath, name, type, -1, 1));
        DraggableNumberField nf = materialNumberField(fieldKey, uniform, scalar);
        syncMaterialNumberValue(uiContext, nf, scalar);
        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        nf.render(r, uiContext, input, theme, valueX, y + 2, valueW, rowH - 4, true);
        return y + rowH;
    }

    private void renderMaterialPrefixedNumber(Ui ui,
                                              UiRenderer r,
                                              UiContext uiContext,
                                              Theme theme,
                                              String fieldKey,
                                              MoudShaderUniform uniform,
                                              float value,
                                              int x,
                                              int y,
                                              int w,
                                              int h,
                                              String prefix) {
        DraggableNumberField nf = materialNumberField(fieldKey, uniform, value);
        syncMaterialNumberValue(uiContext, nf, value);
        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        nf.render(r, uiContext, input, theme, x, y, w, h, true);

        int muted = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
        r.drawText(prefix, x + 4, r.baselineForBox(y, h), muted);
    }

    private static int uniformComponentCount(String glslTypeLower) {
        if (glslTypeLower == null || glslTypeLower.isBlank()) {
            return 1;
        }
        return switch (glslTypeLower) {
            case "vec2", "ivec2", "uvec2", "bvec2" -> 2;
            case "vec3", "ivec3", "uvec3", "bvec3" -> 3;
            case "vec4", "ivec4", "uvec4", "bvec4" -> 4;
            default -> 1;
        };
    }

    private static boolean uniformIsInt(String glslTypeLower) {
        if (glslTypeLower == null) {
            return false;
        }
        String t = glslTypeLower.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("int") || t.startsWith("uint") || t.startsWith("ivec") || t.startsWith("uvec");
    }

    private static float readMaterialScalar(MoudMaterial.Param p) {
        if (p instanceof MoudMaterial.Param.Number num) {
            return num.value();
        }
        if (p instanceof MoudMaterial.Param.Bool b) {
            return b.value() ? 1.0f : 0.0f;
        }
        if (p instanceof MoudMaterial.Param.Vec v && v.values() != null && v.values().length > 0) {
            return v.values()[0];
        }
        return 0.0f;
    }

    private static float[] readMaterialVec(MoudMaterial.Param p, int comps) {
        float[] out = new float[Math.max(1, Math.min(4, comps))];
        if (p instanceof MoudMaterial.Param.Vec v && v.values() != null && v.values().length > 0) {
            float[] src = v.values();
            for (int i = 0; i < out.length; i++) {
                out[i] = i < src.length ? src[i] : 0.0f;
            }
            return out;
        }
        if (p instanceof MoudMaterial.Param.Number num) {
            out[0] = num.value();
        } else if (p instanceof MoudMaterial.Param.Bool b) {
            out[0] = b.value() ? 1.0f : 0.0f;
        }
        return out;
    }

    private DraggableNumberField materialNumberField(String key, MoudShaderUniform uniform, float initial) {
        return materialNumberFields.computeIfAbsent(key, k -> {
            float min = -1_000_000.0f;
            float max = 1_000_000.0f;
            DraggableNumberField nf = new DraggableNumberField(initial, min, max);
            Map<String, String> hints = uniform == null ? Map.of() : uniform.editorHints();
            if (hints != null && !hints.isEmpty()) {
                String step = hints.get("step");
                if (step != null) {
                    nf.setSnapStep(ParseUtils.parseFloat(step, 1.0f));
                }
                float rangeMin = min;
                float rangeMax = max;
                String minS = hints.get("min");
                if (minS != null) {
                    rangeMin = ParseUtils.parseFloat(minS, rangeMin);
                }
                String maxS = hints.get("max");
                if (maxS != null) {
                    rangeMax = ParseUtils.parseFloat(maxS, rangeMax);
                }
                nf.setRange(rangeMin, rangeMax);
            }
            nf.setListener(v -> {
                if (!syncingMaterialNumbers) {
                    pendingMaterialNumbers.put(key, v);
                }
            });
            return nf;
        });
    }

    private void syncMaterialNumberValue(UiContext uiContext, DraggableNumberField nf, float value) {
        if (nf == null) {
            return;
        }
        if (nf.isEditing()) {
            return;
        }
        if (uiContext != null && uiContext.pointer().isCaptured(nf.id())) {
            return;
        }
        syncingMaterialNumbers = true;
        try {
            nf.setValue(value);
        } finally {
            syncingMaterialNumbers = false;
        }
    }

    private void commitMaterialStringField(String fieldKey, String value) {
        MaterialStringBinding binding = materialStringBindings.get(fieldKey);
        if (binding == null) {
            return;
        }
        String next = value == null ? "" : value.trim();
        commitMaterialTexture(binding.materialPath, null, binding.uniformName, next);
    }

    private void toggleMaterialTextureMenu(int x, int y, String materialPath, String uniform) {
        if (materialPath == null || uniform == null) {
            return;
        }
        if (materialTextureMenu.isOpen()
                && Objects.equals(materialTextureMenuMaterialPath, materialPath)
                && Objects.equals(materialTextureMenuUniform, uniform)) {
            materialTextureMenu.close();
            materialTextureMenuMaterialPath = null;
            materialTextureMenuUniform = null;
            return;
        }
        materialTextureMenuMaterialPath = materialPath;
        materialTextureMenuUniform = uniform;
        buildMaterialTextureMenu(materialPath, uniform);
        EditorUiUtil.openMenuClamped(materialTextureMenu, runtime, x, y);
    }

    private void buildMaterialTextureMenu(String materialPath, String uniform) {
        materialTextureMenu.clear();
        materialTextureMenu.addItem("Default", () -> commitMaterialTexture(materialPath, null, uniform, null));
        materialTextureMenu.addItem("Clear", () -> commitMaterialTexture(materialPath, null, uniform, ""));
        materialTextureMenu.addItem("Browse file...", () -> {
            Thread.ofVirtual().start(() -> {
                String result = TinyFileDialogs.tinyfd_openFileDialog("Select Texture", "", null, "Image Files", false);
                if (result == null || result.isBlank()) {
                    return;
                }
                String trimmed = result.trim();
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.execute(() -> commitMaterialTexture(materialPath, null, uniform, trimmed));
                } else {
                    commitMaterialTexture(materialPath, null, uniform, trimmed);
                }
            });
        });
        materialTextureMenu.addSeparator();
        materialTextureMenu.addItem(MoudTextures.WHITE_ID.toString(), () -> commitMaterialTexture(materialPath, null, uniform, MoudTextures.WHITE_ID.toString()));

        List<String> images = MoudTextures.imageAssetPaths();
        if (images.isEmpty()) {
            materialTextureMenu.addSeparator();
            materialTextureMenu.addItem("(No res:// images)", () -> {
            });
            return;
        }

        materialTextureMenu.addSeparator();
        for (String path : images) {
            if (path == null || path.isBlank()) {
                continue;
            }
            materialTextureMenu.addItem(path, () -> commitMaterialTexture(materialPath, null, uniform, path));
        }
    }

    private void commitMaterialBool(String materialPath, String shaderPath, String uniformName, boolean value) {
        if (materialPath == null || materialPath.isBlank() || uniformName == null || uniformName.isBlank()) {
            return;
        }
        updateMaterialAsset(materialPath, shaderPath, params -> params.put(uniformName, new MoudMaterial.Param.Bool(value)));
    }

    private void commitMaterialTexture(String materialPath, String shaderPath, String uniformName, String textureRef) {
        if (materialPath == null || materialPath.isBlank() || uniformName == null || uniformName.isBlank()) {
            return;
        }
        updateMaterialAsset(materialPath, shaderPath, params -> {
            if (textureRef == null) {
                params.remove(uniformName);
            } else {
                params.put(uniformName, new MoudMaterial.Param.Texture(textureRef));
            }
        });
    }

    private void applyMaterialEdits(String materialPath, List<MaterialEdit> edits) {
        if (materialPath == null || materialPath.isBlank() || edits == null || edits.isEmpty()) {
            return;
        }
        String shaderFallback = null;
        for (MaterialEdit edit : edits) {
            if (edit != null && edit.binding != null && edit.binding.shaderPath != null && !edit.binding.shaderPath.isBlank()) {
                shaderFallback = edit.binding.shaderPath;
                break;
            }
        }
        String finalShaderFallback = shaderFallback;
        updateMaterialAsset(materialPath, finalShaderFallback, params -> {
            for (MaterialEdit edit : edits) {
                if (edit == null || edit.binding == null) {
                    continue;
                }
                MaterialNumberBinding b = edit.binding;
                String uniform = b.uniformName;
                if (uniform == null || uniform.isBlank()) {
                    continue;
                }
                float v = edit.value;
                boolean isInt = uniformIsInt(b.glslType);
                if (isInt) {
                    v = Math.round(v);
                }
                if (b.componentCount <= 1 || b.componentIndex < 0) {
                    params.put(uniform, new MoudMaterial.Param.Number(v));
                    continue;
                }
                float[] vec = readMaterialVec(params.get(uniform), b.componentCount);
                if (b.componentIndex < vec.length) {
                    vec[b.componentIndex] = v;
                }
                params.put(uniform, new MoudMaterial.Param.Vec(vec));
            }
        });
    }

    private void updateMaterialAsset(String materialPath, String shaderFallback, Consumer<HashMap<String, MoudMaterial.Param>> editor) {
        if (materialPath == null || materialPath.isBlank() || !materialPath.startsWith(ResPath.SCHEME) || editor == null) {
            return;
        }

        String currentText = MoudTextAssets.readText(materialPath);
        MoudMaterial parsed = currentText == null ? null : MoudMaterialParser.parse(currentText);
        MoudMaterial base = parsed;
        if (base == null && shaderFallback != null && !shaderFallback.isBlank()) {
            base = new MoudMaterial(shaderFallback.trim(), Map.of());
        }
        if (base == null) {
            return;
        }

        HashMap<String, MoudMaterial.Param> params = new HashMap<>(base.params());
        editor.accept(params);
        MoudMaterial next = new MoudMaterial(base.shader(), Map.copyOf(params));
        String json = MoudMaterialWriter.toJson(next);
        MoudTextAssets.overrideText(materialPath, json);

        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets == null || session == null) {
            return;
        }
        try {
            assets.upload(session, new ResPath(materialPath), json.getBytes(StandardCharsets.UTF_8), AssetType.TEXT);
        } catch (Exception e) {
            String msg = e.getMessage();
            runtime.requestToast("Material update failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
        }
    }

    private static final class MaterialStringBinding {
        final String materialPath;
        final String uniformName;

        MaterialStringBinding(String materialPath, String uniformName) {
            this.materialPath = materialPath == null ? "" : materialPath;
            this.uniformName = uniformName == null ? "" : uniformName;
        }
    }

    private static final class MaterialNumberBinding {
        final String materialPath;
        final String shaderPath;
        final String uniformName;
        final String glslType;
        final int componentIndex;
        final int componentCount;

        MaterialNumberBinding(String materialPath, String shaderPath, String uniformName, String glslType, int componentIndex, int componentCount) {
            this.materialPath = materialPath == null ? "" : materialPath;
            this.shaderPath = shaderPath == null ? "" : shaderPath;
            this.uniformName = uniformName == null ? "" : uniformName;
            this.glslType = glslType == null ? "" : glslType;
            this.componentIndex = componentIndex;
            this.componentCount = componentCount;
        }
    }

    private static final class MaterialEdit {
        final MaterialNumberBinding binding;
        final float value;

        MaterialEdit(MaterialNumberBinding binding, float value) {
            this.binding = binding;
            this.value = value;
        }
    }
}
