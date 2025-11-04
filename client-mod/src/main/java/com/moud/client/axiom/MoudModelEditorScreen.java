package com.moud.client.axiom;

import com.moud.api.math.Quaternion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;
import java.util.function.DoubleConsumer;

public class MoudModelEditorScreen extends AbstractMoudMarkerScreen {
    private static final double SCALE_MIN = 0.1d;
    private static final double SCALE_MAX = 10.0d;
    private static final double SCALE_STEP = 0.01d;

    private double scaleX;
    private double scaleY;
    private double scaleZ;
    private float pitch;
    private float yaw;
    private float roll;
    private final String modelPath;
    private String texture;

    private TextFieldWidget textureField;

    public MoudModelEditorScreen(UUID markerId, NbtCompound data) {
        super(markerId, data, Text.literal("Edit Moud Model"));
        this.scaleX = getDouble(data, "scaleX", 1.0d);
        this.scaleY = getDouble(data, "scaleY", 1.0d);
        this.scaleZ = getDouble(data, "scaleZ", 1.0d);
        this.pitch = (float) getDouble(data, "rotationPitch", 0.0d);
        this.yaw = (float) getDouble(data, "rotationYaw", 0.0d);
        this.roll = (float) getDouble(data, "rotationRoll", 0.0d);
        this.modelPath = data.contains("modelPath", NbtElement.STRING_TYPE) ? data.getString("modelPath") : "";
        this.texture = data.contains("texture", NbtElement.STRING_TYPE) ? data.getString("texture") : "";
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = 60;
        int sliderWidth = 210;

        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Scale X"), SCALE_MIN, SCALE_MAX, this.scaleX, SCALE_STEP,
                value -> this.scaleX = value));
        y += 26;
        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Scale Y"), SCALE_MIN, SCALE_MAX, this.scaleY, SCALE_STEP,
                value -> this.scaleY = value));
        y += 26;
        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Scale Z"), SCALE_MIN, SCALE_MAX, this.scaleZ, SCALE_STEP,
                value -> this.scaleZ = value));

        y += 32;
        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Pitch"), -180.0d, 180.0d, this.pitch, 1.0d,
                value -> this.pitch = (float) value));
        y += 26;
        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Yaw"), -180.0d, 180.0d, this.yaw, 1.0d,
                value -> this.yaw = (float) value));
        y += 26;
        this.addDrawableChild(new LabeledSlider(centerX - sliderWidth / 2, y, sliderWidth, 20,
                Text.literal("Roll"), -180.0d, 180.0d, this.roll, 1.0d,
                value -> this.roll = (float) value));

        y += 40;
        this.textureField = new TextFieldWidget(this.textRenderer, centerX - sliderWidth / 2, y, sliderWidth, 20, Text.literal("Texture"));
        this.textureField.setMaxLength(256);
        this.textureField.setText(this.texture);
        this.addDrawableChild(this.textureField);

        y += 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> applyChanges(false))
                .dimensions(centerX - 110, y, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> applyChanges(true))
                .dimensions(centerX + 10, y, 100, 20).build());

        this.setInitialFocus(this.textureField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 30, 0xFFFFFF);
        if (!this.modelPath.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Model: " + this.modelPath), centerX, 45, 0xA0A0A0);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal("Texture"),
                centerX - 105, this.textureField.getY() - 12, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.closeScreen();
    }

    private void applyChanges(boolean closeAfter) {
        this.texture = this.textureField.getText().trim();

        NbtCompound payload = new NbtCompound();
        payload.putString("moudType", "model");
        payload.putDouble("scaleX", this.scaleX);
        payload.putDouble("scaleY", this.scaleY);
        payload.putDouble("scaleZ", this.scaleZ);

        NbtCompound scaleCompound = new NbtCompound();
        scaleCompound.putDouble("x", this.scaleX);
        scaleCompound.putDouble("y", this.scaleY);
        scaleCompound.putDouble("z", this.scaleZ);
        payload.put("scale", scaleCompound);

        payload.putDouble("rotationPitch", this.pitch);
        payload.putDouble("rotationYaw", this.yaw);
        payload.putDouble("rotationRoll", this.roll);

        NbtCompound rotationEuler = new NbtCompound();
        rotationEuler.putDouble("pitch", this.pitch);
        rotationEuler.putDouble("yaw", this.yaw);
        rotationEuler.putDouble("roll", this.roll);
        payload.put("rotation", rotationEuler);

        Quaternion quaternion = Quaternion.fromEuler(this.pitch, this.yaw, this.roll);
        NbtCompound rotationQuat = new NbtCompound();
        rotationQuat.putDouble("x", quaternion.x);
        rotationQuat.putDouble("y", quaternion.y);
        rotationQuat.putDouble("z", quaternion.z);
        rotationQuat.putDouble("w", quaternion.w);
        payload.put("rotationQuat", rotationQuat);

        if (!this.texture.isEmpty()) {
            payload.putString("texture", this.texture);
        }

        this.sendPayload(payload);
        if (closeAfter) {
            this.closeScreen();
        }
    }

    private static double getDouble(NbtCompound compound, String key, double fallback) {
        return compound != null && compound.contains(key, NbtElement.DOUBLE_TYPE)
                ? compound.getDouble(key)
                : fallback;
    }

    private static class LabeledSlider extends SliderWidget {
        private final Text label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer consumer;

        LabeledSlider(int x, int y, int width, int height, Text label,
                      double min, double max, double initialValue, double step,
                      DoubleConsumer consumer) {
            super(x, y, width, height, Text.empty(), 0.0d);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.consumer = consumer;
            double clamped = MathHelper.clamp((initialValue - min) / (max - min), 0.0d, 1.0d);
            this.value = clamped;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double scaled = getScaledValue();
            this.setMessage(Text.literal(String.format("%s: %.2f", this.label.getString(), scaled)));
        }

        @Override
        protected void applyValue() {
            double scaled = getScaledValue();
            this.consumer.accept(scaled);
            this.updateMessage();
        }

        private double getScaledValue() {
            double raw = this.value * (this.max - this.min) + this.min;
            if (this.step > 0.0d) {
                raw = Math.round(raw / this.step) * this.step;
            }
            return MathHelper.clamp(raw, this.min, this.max);
        }
    }
}
