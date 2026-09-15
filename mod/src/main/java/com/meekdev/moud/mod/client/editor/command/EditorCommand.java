package com.meekdev.moud.mod.client.editor.command;

import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

public record EditorCommand(String id, String menu, String label, @Nullable Shortcut shortcut, BooleanSupplier available, Runnable action,
                            boolean inMenu) {

    public EditorCommand(String id, String menu, String label, @Nullable Shortcut shortcut, BooleanSupplier available, Runnable action) {
        this(id, menu, label, shortcut, available, action, true);
    }

    public EditorCommand hidden() {
        return new EditorCommand(id, menu, label, shortcut, available, action, false);
    }

    public void runIfAvailable() {
        if (available.getAsBoolean()) action.run();
    }
}
