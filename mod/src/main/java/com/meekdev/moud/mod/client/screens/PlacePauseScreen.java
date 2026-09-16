package com.meekdev.moud.mod.client.screens;

import com.meekdev.moud.mod.client.GameState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

public final class PlacePauseScreen extends Screen {

    private static final int BUTTON_WIDTH = 160;
    private static final int GAP = 24;

    public PlacePauseScreen() {
        super(Component.literal("Paused"));
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = height / 2 - GAP;
        addRenderableWidget(Button.builder(Component.literal("Resume"), button -> onClose()).bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options, true)))
                .bounds(x, y + GAP, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Quit"), button -> GameState.INSTANCE.quit()).bounds(x, y + GAP * 2, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - GAP * 2, 0xFFFFFFFF);
    }
}
