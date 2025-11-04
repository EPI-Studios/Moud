package com.moud.client.axiom;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MoudLightEditorScreen extends AbstractMoudMarkerScreen {
    private final Map<String, TextFieldWidget> numericFields = new LinkedHashMap<>();
    private TextFieldWidget typeField;

    public MoudLightEditorScreen(UUID markerId, NbtCompound data) {
        super(markerId, data, Text.literal("Edit Moud Light"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = 70;
        int columnOffset = 130;
        int fieldWidth = 110;

        this.typeField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY - 35, fieldWidth, 20, Text.literal("Type"));
        this.typeField.setMaxLength(64);
        this.typeField.setText(this.originalData.contains("type", NbtElement.STRING_TYPE) ? this.originalData.getString("type") : "");
        this.addDrawableChild(this.typeField);

        String[] leftKeys = {"radius", "width", "height", "distance", "angle"};
        String[] rightKeys = {"brightness", "r", "g", "b", "dirX", "dirY", "dirZ"};

        int y = startY;
        for (String key : leftKeys) {
            TextFieldWidget field = createNumberField(centerX - columnOffset - fieldWidth / 2, y, getDouble(key, 0.0d));
            numericFields.put(key, field);
            y += 24;
        }

        y = startY;
        for (String key : rightKeys) {
            TextFieldWidget field = createNumberField(centerX + columnOffset - fieldWidth / 2, y, getDouble(key, 0.0d));
            numericFields.put(key, field);
            y += 24;
        }

        int buttonsY = Math.max(startY + leftKeys.length * 24, startY + rightKeys.length * 24) + 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> applyChanges(false))
                .dimensions(centerX - 110, buttonsY, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> applyChanges(true))
                .dimensions(centerX + 10, buttonsY, 100, 20).build());

        this.setInitialFocus(this.typeField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 30, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Type"),
                centerX - 60, this.typeField.getY() - 12, 0xFFFFFF);

        for (Map.Entry<String, TextFieldWidget> entry : numericFields.entrySet()) {
            Text label = Text.literal(entry.getKey());
            TextFieldWidget field = entry.getValue();
            context.drawTextWithShadow(this.textRenderer, label,
                    field.getX(), field.getY() - 12, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        this.closeScreen();
    }

    private void applyChanges(boolean closeAfter) {
        NbtCompound payload = new NbtCompound();
        payload.putString("moudType", "light");

        String type = this.typeField.getText().trim();
        if (!type.isEmpty()) {
            payload.putString("type", type);
        }

        for (Map.Entry<String, TextFieldWidget> entry : numericFields.entrySet()) {
            double value = parseDouble(entry.getValue().getText(), getDouble(entry.getKey(), 0.0d));
            payload.putDouble(entry.getKey(), value);
        }

        this.sendPayload(payload);
        if (closeAfter) {
            this.closeScreen();
        }
    }

    private TextFieldWidget createNumberField(int x, int y, double initial) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, 110, 20, Text.literal("Value"));
        field.setMaxLength(64);
        field.setText(String.format("%.4f", initial));
        this.addDrawableChild(field);
        return field;
    }

    private double getDouble(String key, double fallback) {
        return this.originalData.contains(key, NbtElement.DOUBLE_TYPE) ? this.originalData.getDouble(key) : fallback;
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
