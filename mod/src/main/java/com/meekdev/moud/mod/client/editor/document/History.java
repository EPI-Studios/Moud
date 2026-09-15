package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.mod.MoudMod;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class History {

    private static final int MAX_ENTRIES = 200;

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final SceneDocument document;

    History(SceneDocument document) {
        this.document = document;
    }

    public Optional<String> execute(Edit edit) {
        Entry top = undoStack.peek();
        boolean continues = top != null && top.open && edit.gesture() != null && Objects.equals(edit.gesture(), top.gesture);
        Edit inverse = null;
        if (!continues) {
            try {
                inverse = edit.invert(document);
            } catch (RuntimeException e) {
                return Optional.of(reason(e));
            }
        }
        try {
            edit.apply(document);
        } catch (RuntimeException e) {
            return Optional.of(reason(e));
        }
        if (continues) {
            top.forward = edit;
        } else {
            undoStack.push(new Entry(edit, inverse, edit.gesture()));
            while (undoStack.size() > MAX_ENTRIES) undoStack.pollLast();
        }
        redoStack.clear();
        return Optional.empty();
    }

    public void settle(boolean gestureHeld) {
        if (gestureHeld) return;
        Entry top = undoStack.peek();
        if (top != null) top.open = false;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        Entry entry = undoStack.poll();
        if (entry == null) return;
        entry.open = false;
        if (!swap(entry, entry.inverse)) return;
        redoStack.push(entry);
    }

    public void redo() {
        Entry entry = redoStack.poll();
        if (entry == null) return;
        if (!swap(entry, entry.forward)) return;
        undoStack.push(entry);
    }

    private boolean swap(Entry entry, Edit run) {
        Edit refreshed;
        try {
            refreshed = run.invert(document);
            run.apply(document);
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("could not {} {}: {}", run == entry.inverse ? "undo" : "redo", entry.forward.label(), reason(e));
            return false;
        }
        if (run == entry.inverse) entry.forward = refreshed;
        else entry.inverse = refreshed;
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public Optional<String> undoLabel() {
        return Optional.ofNullable(undoStack.peek()).map(entry -> entry.forward.label());
    }

    public Optional<String> redoLabel() {
        return Optional.ofNullable(redoStack.peek()).map(entry -> entry.forward.label());
    }

    private static String reason(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static final class Entry {
        Edit forward;
        Edit inverse;
        final @Nullable String gesture;
        boolean open;

        Entry(Edit forward, Edit inverse, @Nullable String gesture) {
            this.forward = forward;
            this.inverse = inverse;
            this.gesture = gesture;
            this.open = gesture != null;
        }
    }
}
