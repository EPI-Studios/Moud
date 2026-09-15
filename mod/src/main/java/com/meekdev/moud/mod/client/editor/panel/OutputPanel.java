package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import java.util.List;

public final class OutputPanel implements Panel {

    public static final String ID = "output";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Output";
    }

    @Override
    public void render() {
        EmptyStates.centered("Output", List.of("Script errors and prints show here."));
    }
}
