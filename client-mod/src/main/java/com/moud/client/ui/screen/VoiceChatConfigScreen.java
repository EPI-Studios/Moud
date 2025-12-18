package com.moud.client.ui.screen;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.audio.VoiceChatController;
import com.moud.client.audio.VoiceKeybindManager;
import com.moud.client.settings.VoiceSettingsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public final class VoiceChatConfigScreen extends Screen {

    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 2;
    private static final int SECTION_SPACING = 8;
    private static final int PANEL_PADDING = 12;
    private static final int PANEL_WIDTH = 300;

    private static final int COLOR_PANEL_BG = 0xF0101010;
    private static final int COLOR_SECTION_HEADER = 0xFFE0E0E0;
    private static final int COLOR_DESCRIPTION = 0xFF808080;
    private static final int COLOR_METER_BG = 0xFF2A2A2A;
    private static final int COLOR_METER_LOW = 0xFF4CAF50;
    private static final int COLOR_METER_MED = 0xFFFFEB3B;
    private static final int COLOR_METER_HIGH = 0xFFF44336;

    private final @Nullable Screen parent;
    private final VoiceSettingsManager.VoiceSettings working;
    private final VoiceSettingsManager.VoiceSettings original;

    private Layout layout;

    private CyclingButtonWidget<VoiceSettingsManager.ActivationMode> activationMode;
    private CyclingButtonWidget<Boolean> microphoneMuted;
    private CyclingButtonWidget<Boolean> dropSilence;
    private CyclingButtonWidget<String> inputDevice;
    private VolumeSlider inputGain;
    private VolumeSlider outputVolume;
    private ThresholdSlider threshold;
    private ButtonWidget micTestButton;

    private boolean micTesting = false;
    private float currentMicLevel = 0.0f;
    private long lastMicLevelUpdate = 0;

    public VoiceChatConfigScreen(@Nullable Screen parent) {
        super(Text.translatable("moud.voice.screen.title"));
        this.parent = parent;
        this.original = VoiceSettingsManager.get().copy();
        this.working = this.original.copy();
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;
        int contentX = panelX + PANEL_PADDING;
        int contentWidth = PANEL_WIDTH - (PANEL_PADDING * 2);
        int halfWidth = (contentWidth - 4) / 2;

        int totalHeight = calculateTotalHeight();
        int panelY = Math.max(10, (height - totalHeight) / 2);

        int y = panelY + PANEL_PADDING;
        int sectionInputY = y;
        y += 14;

        inputDevice = addDrawableChild(buildInputDeviceSelector(contentX, y, contentWidth));
        inputDevice.setTooltip(Tooltip.of(Text.translatable("moud.voice.input_device.tooltip")));
        y += BUTTON_HEIGHT + ROW_SPACING;

        inputGain = addDrawableChild(new VolumeSlider(
                contentX, y, halfWidth, BUTTON_HEIGHT,
                Text.translatable("moud.voice.input_gain_short"),
                working.inputGain, 0, 200,
                value -> working.inputGain = value
        ));
        inputGain.setTooltip(Tooltip.of(Text.translatable("moud.voice.input_gain.tooltip")));

        micTestButton = addDrawableChild(ButtonWidget
                .builder(Text.translatable("moud.voice.mic_test"), this::toggleMicTest)
                .dimensions(contentX + halfWidth + 4, y, halfWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("moud.voice.mic_test.tooltip")))
                .build());
        y += BUTTON_HEIGHT + ROW_SPACING;

        int micMeterY = y;
        y += 14 + SECTION_SPACING;

        int sectionOutputY = y;
        y += 14;

        outputVolume = addDrawableChild(new VolumeSlider(
                contentX, y, contentWidth, BUTTON_HEIGHT,
                Text.translatable("moud.voice.output_volume"),
                working.outputVolume, 0, 200,
                value -> working.outputVolume = value
        ));
        outputVolume.setTooltip(Tooltip.of(Text.translatable("moud.voice.output_volume.tooltip")));
        y += BUTTON_HEIGHT + SECTION_SPACING;

        int sectionActivationY = y;
        y += 14;

        activationMode = addDrawableChild(CyclingButtonWidget
                .<VoiceSettingsManager.ActivationMode>builder(mode -> {
                    if (mode == VoiceSettingsManager.ActivationMode.PUSH_TO_TALK) {
                        return Text.translatable("moud.voice.activation.ptt");
                    }
                    return Text.translatable("moud.voice.activation.vad");
                })
                .values(
                        VoiceSettingsManager.ActivationMode.PUSH_TO_TALK,
                        VoiceSettingsManager.ActivationMode.VOICE_ACTIVITY
                )
                .initially(working.activationMode)
                .build(
                        contentX,
                        y,
                        halfWidth,
                        BUTTON_HEIGHT,
                        Text.translatable("moud.voice.mode"),
                        (button, value) -> {
                            working.activationMode = value;
                            updateWidgetState();
                        }
                ));
        activationMode.setTooltip(Tooltip.of(Text.translatable("moud.voice.activation_mode.tooltip")));

        microphoneMuted = addDrawableChild(CyclingButtonWidget.onOffBuilder(
                        Text.translatable("options.on"),
                        Text.translatable("options.off"))
                .initially(working.microphoneMuted)
                .build(
                        contentX + halfWidth + 4,
                        y,
                        halfWidth,
                        BUTTON_HEIGHT,
                        Text.translatable("moud.voice.muted"),
                        (button, value) -> working.microphoneMuted = value
                ));
        microphoneMuted.setTooltip(Tooltip.of(Text.translatable("moud.voice.mic_muted.tooltip")));
        y += BUTTON_HEIGHT + ROW_SPACING;

        dropSilence = addDrawableChild(CyclingButtonWidget.onOffBuilder(
                        Text.translatable("options.on"),
                        Text.translatable("options.off"))
                .initially(working.autoMuteWhenIdle)
                .build(
                        contentX,
                        y,
                        halfWidth,
                        BUTTON_HEIGHT,
                        Text.translatable("moud.voice.silence"),
                        (button, value) -> working.autoMuteWhenIdle = value
                ));
        dropSilence.setTooltip(Tooltip.of(Text.translatable("moud.voice.drop_silence.tooltip")));

        threshold = addDrawableChild(new ThresholdSlider(
                contentX + halfWidth + 4,
                y,
                halfWidth,
                BUTTON_HEIGHT,
                working.activityThreshold,
                value -> working.activityThreshold = value
        ));
        threshold.setTooltip(Tooltip.of(Text.translatable("moud.voice.vad_threshold.tooltip")));
        y += BUTTON_HEIGHT + SECTION_SPACING;

        int keybindsY = y;
        y += 24 + SECTION_SPACING;

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> applyAndClose())
                .dimensions(contentX, y, halfWidth, BUTTON_HEIGHT)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> cancelAndClose())
                .dimensions(contentX + halfWidth + 4, y, halfWidth, BUTTON_HEIGHT)
                .build());

        y += BUTTON_HEIGHT + PANEL_PADDING;

        int panelHeight = y - panelY;
        this.layout = new Layout(panelX, panelY, PANEL_WIDTH, panelHeight, contentX, contentWidth,
                sectionInputY, sectionOutputY, sectionActivationY, micMeterY, keybindsY);

        updateWidgetState();
    }

    private int calculateTotalHeight() {
        return PANEL_PADDING * 2 + // top/bottom padding
                14 * 3 +            // 3 section headers
                BUTTON_HEIGHT * 7 + // 7 rows of buttons/sliders
                ROW_SPACING * 4 +   // row spacing
                SECTION_SPACING * 4 + // section spacing
                14 +                // mic meter
                24;                 // keybinds info
    }

    private CyclingButtonWidget<String> buildInputDeviceSelector(int x, int y, int width) {
        List<String> values = new ArrayList<>();
        values.add("");

        ClientAPIService api = ClientAPIService.INSTANCE;
        if (api != null && api.audio != null) {
            values.addAll(api.audio.getMicrophone().getInputDevices());
        }

        String current = working.inputDeviceName == null ? "" : working.inputDeviceName;
        if (!values.contains(current)) {
            values.add(current);
        }

        CyclingButtonWidget.Builder<String> builder = CyclingButtonWidget.<String>builder(device ->
                device == null || device.isBlank()
                        ? Text.translatable("moud.voice.device.default")
                        : Text.literal(truncateDeviceName(device, 30))
        ).values(values);

        builder.initially(current);
        return builder.build(x, y, width, BUTTON_HEIGHT, Text.translatable("moud.voice.device"), (button, value) -> {
            working.inputDeviceName = value == null ? "" : value;
            VoiceChatController.refreshDevicePreference(working.inputDeviceName);
        });
    }

    private String truncateDeviceName(String name, int maxChars) {
        if (name == null) return "";
        if (name.length() <= maxChars) return name;
        return name.substring(0, maxChars - 3) + "...";
    }

    private void toggleMicTest(ButtonWidget button) {
        micTesting = !micTesting;
        ClientAPIService api = ClientAPIService.INSTANCE;

        if (micTesting) {
            button.setMessage(Text.translatable("moud.voice.mic_test.stop"));
            if (api != null && api.audio != null) {
                api.audio.getMicrophone().start(Map.of("sessionId", "moud:settings-test"));
            }
        } else {
            button.setMessage(Text.translatable("moud.voice.mic_test"));
            if (api != null && api.audio != null) {
                api.audio.getMicrophone().stop();
            }
            currentMicLevel = 0.0f;
        }
    }

    private void updateWidgetState() {
        boolean voiceActivity = working.activationMode == VoiceSettingsManager.ActivationMode.VOICE_ACTIVITY;
        if (dropSilence != null) {
            dropSilence.active = voiceActivity;
        }
        if (threshold != null) {
            threshold.active = voiceActivity;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (micTesting) {
            ClientAPIService api = ClientAPIService.INSTANCE;
            if (api != null && api.audio != null && api.audio.getMicrophone().isActive()) {
                float level = api.audio.getMicrophone().getCurrentLevel();
                float gainMultiplier = working.inputGain / 100.0f;
                currentMicLevel = Math.min(1.0f, level * gainMultiplier);
                lastMicLevelUpdate = System.currentTimeMillis();
            }
        } else {
            if (System.currentTimeMillis() - lastMicLevelUpdate > 100) {
                currentMicLevel = Math.max(0, currentMicLevel - 0.05f);
            }
        }
    }

    private void applyAndClose() {
        stopMicTest();

        VoiceSettingsManager.VoiceSettings target = VoiceSettingsManager.get();
        target.inputDeviceName = working.inputDeviceName == null ? "" : working.inputDeviceName;
        target.activationMode = working.activationMode;
        target.microphoneMuted = working.microphoneMuted;
        target.deafened = working.deafened;
        target.autoMuteWhenIdle = working.autoMuteWhenIdle;
        target.automaticVoiceDetection = working.automaticVoiceDetection;
        target.activityThreshold = working.activityThreshold;
        target.inputGain = working.inputGain;
        target.outputVolume = working.outputVolume;
        VoiceSettingsManager.save();

        VoiceChatController.refreshDevicePreference(target.inputDeviceName);

        if (target.microphoneMuted) {
            ClientAPIService api = ClientAPIService.INSTANCE;
            if (api != null && api.audio != null) {
                api.audio.getMicrophone().stop();
            }
        }

        close();
    }

    private void cancelAndClose() {
        stopMicTest();

        VoiceSettingsManager.VoiceSettings target = VoiceSettingsManager.get();
        target.inputDeviceName = original.inputDeviceName;
        target.activationMode = original.activationMode;
        target.microphoneMuted = original.microphoneMuted;
        target.deafened = original.deafened;
        target.autoMuteWhenIdle = original.autoMuteWhenIdle;
        target.automaticVoiceDetection = original.automaticVoiceDetection;
        target.activityThreshold = original.activityThreshold;
        target.inputGain = original.inputGain;
        target.outputVolume = original.outputVolume;
        VoiceSettingsManager.save();

        VoiceChatController.refreshDevicePreference(target.inputDeviceName);
        close();
    }

    private void stopMicTest() {
        if (micTesting) {
            micTesting = false;
            ClientAPIService api = ClientAPIService.INSTANCE;
            if (api != null && api.audio != null) {
                api.audio.getMicrophone().stop();
            }
        }
    }

    @Override
    public void close() {
        stopMicTest();
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout current = layout;
        if (current != null) {
            context.fill(current.panelX, current.panelY,
                    current.panelX + current.panelWidth, current.panelY + current.panelHeight,
                    COLOR_PANEL_BG);

            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, current.panelY + 4, 0xFFFFFF);

            renderSectionHeader(context, "moud.voice.section.input", current.contentX, current.sectionInputY);
            renderSectionHeader(context, "moud.voice.section.output", current.contentX, current.sectionOutputY);
            renderSectionHeader(context, "moud.voice.section.activation", current.contentX, current.sectionActivationY);

            renderMicMeter(context, current.contentX, current.micMeterY, current.contentWidth, 12);

            renderKeybindsInfo(context, current.contentX, current.keybindsY, current.contentWidth);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSectionHeader(DrawContext context, String translationKey, int x, int y) {
        Text header = Text.translatable(translationKey).formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, header, x, y, COLOR_SECTION_HEADER);
    }

    private void renderKeybindsInfo(DrawContext context, int x, int y, int width) {
        String ptt = VoiceKeybindManager.PUSH_TO_TALK.getBoundKeyLocalizedText().getString();
        String mute = VoiceKeybindManager.TOGGLE_MUTE.getBoundKeyLocalizedText().getString();
        String deafen = VoiceKeybindManager.TOGGLE_DEAFEN.getBoundKeyLocalizedText().getString();

        Text line1 = Text.literal("PTT: ").formatted(Formatting.GRAY)
                .append(Text.literal(ptt).formatted(Formatting.YELLOW))
                .append(Text.literal("  Mute: ").formatted(Formatting.GRAY))
                .append(Text.literal(mute).formatted(Formatting.YELLOW))
                .append(Text.literal("  Deafen: ").formatted(Formatting.GRAY))
                .append(Text.literal(deafen).formatted(Formatting.YELLOW));

        context.drawCenteredTextWithShadow(textRenderer, line1, x + width / 2, y, 0xFFFFFF);

        Text line2 = Text.translatable("moud.voice.keybinds.hint").formatted(Formatting.DARK_GRAY);
        context.drawCenteredTextWithShadow(textRenderer, line2, x + width / 2, y + 11, 0xFFFFFF);
    }

    private void renderMicMeter(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, COLOR_METER_BG);

        if (currentMicLevel > 0) {
            int meterWidth = (int) (width * Math.min(1.0f, currentMicLevel));
            int color;
            if (currentMicLevel < 0.5f) {
                color = COLOR_METER_LOW;
            } else if (currentMicLevel < 0.8f) {
                color = COLOR_METER_MED;
            } else {
                color = COLOR_METER_HIGH;
            }
            context.fill(x, y, x + meterWidth, y + height, color);
        }

        if (working.activationMode == VoiceSettingsManager.ActivationMode.VOICE_ACTIVITY) {
            float thresholdNormalized = working.activityThreshold / 80.0f;
            int thresholdX = x + (int) (width * (1.0f - thresholdNormalized));
            context.fill(thresholdX, y, thresholdX + 1, y + height, 0xFFFFFFFF);
        }

        Text label = micTesting
                ? Text.literal("Listening...").formatted(Formatting.GREEN)
                : Text.literal("Click Test Mic").formatted(Formatting.DARK_GRAY);
        context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2, y + 2, 0xFFFFFF);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth,
                          int sectionInputY, int sectionOutputY, int sectionActivationY,
                          int micMeterY, int keybindsY) {
    }

    private static final class VolumeSlider extends SliderWidget {
        private final Text label;
        private final int min;
        private final int max;
        private final IntConsumer consumer;

        VolumeSlider(int x,
                     int y,
                     int width,
                     int height,
                     Text label,
                     int initialValue,
                     int min,
                     int max,
                     IntConsumer consumer) {
            super(x, y, width, height, Text.empty(), toSliderValue(initialValue, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.consumer = consumer;
            updateMessage();
        }

        private static double toSliderValue(int value, int min, int max) {
            int clamped = Math.max(min, Math.min(max, value));
            return (clamped - min) / (double) (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(label.copy().append(": " + currentValue() + "%"));
        }

        @Override
        protected void applyValue() {
            consumer.accept(currentValue());
        }

        private int currentValue() {
            return (int) Math.round(min + value * (max - min));
        }
    }

    private static final class ThresholdSlider extends SliderWidget {
        private static final int MIN_THRESHOLD_DB = 10;
        private static final int MAX_THRESHOLD_DB = 80;
        private final IntConsumer consumer;

        ThresholdSlider(int x, int y, int width, int height, int initialThresholdDb, IntConsumer consumer) {
            super(x, y, width, height, Text.empty(), toSliderValue(initialThresholdDb));
            this.consumer = consumer;
            updateMessage();
        }

        private static double toSliderValue(int thresholdDb) {
            int clamped = Math.max(MIN_THRESHOLD_DB, Math.min(MAX_THRESHOLD_DB, thresholdDb));
            return (clamped - MIN_THRESHOLD_DB) / (double) (MAX_THRESHOLD_DB - MIN_THRESHOLD_DB);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("VAD: " + currentThresholdDb() + "dB"));
        }

        @Override
        protected void applyValue() {
            consumer.accept(currentThresholdDb());
        }

        private int currentThresholdDb() {
            return (int) Math.round(MIN_THRESHOLD_DB + value * (MAX_THRESHOLD_DB - MIN_THRESHOLD_DB));
        }
    }
}
