package com.meekdev.moud.mod.client.screens;

import com.meekdev.moud.mod.client.GameState;
import com.meekdev.moud.mod.client.Launch;
import com.meekdev.moud.mod.place.PlaceToml;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class PlaceMessageScreen extends Screen {

    private static final int BUTTON_WIDTH = 120;
    private static final int LINE = 11;

    private final Component reason;

    public PlaceMessageScreen(Component title, Component reason) {
        super(title);
        this.reason = reason;
    }

    @Override
    protected void init() {
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(Component.literal("Try again"), button -> Launch.openProject(PlaceToml.root()))
                .bounds(width / 2 - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Quit"), button -> GameState.INSTANCE.quit())
                .bounds(width / 2 + 4, y, BUTTON_WIDTH, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        LoadingLook.draw(graphics, width, height);
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        List<FormattedCharSequence> lines = font.split(reason, Math.min(width - 40, 360));
        int y = height / 2 - 20;
        for (FormattedCharSequence line : lines) {
            graphics.centeredText(font, line, width / 2, y, 0xFFCCCCCC);
            y += LINE;
        }
    }
}
