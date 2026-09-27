package com.meekdev.moud.mod.client.editor.command;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public record EditorCommand(String id, String menu, String label, @Nullable Shortcut shortcut, BooleanSupplier available, Runnable action,
                            boolean inMenu, @Nullable Supplier<String> naming) {

    public EditorCommand(String id, String menu, String label, @Nullable Shortcut shortcut, BooleanSupplier available, Runnable action) {
        this(id, menu, label, shortcut, available, action, true, null);
    }

    public EditorCommand hidden() {
        return new EditorCommand(id, menu, label, shortcut, available, action, false, naming);
    }

    public EditorCommand named(Supplier<String> by) {
        return new EditorCommand(id, menu, label, shortcut, available, action, inMenu, by);
    }

    public String shown() {
        return naming == null ? label : naming.get();
    }

    public void runIfAvailable() {
        if (available.getAsBoolean()) action.run();
    }
}
