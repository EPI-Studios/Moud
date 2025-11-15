package com.moud.client.editor.scene;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneHistoryManager {
    private static final SceneHistoryManager INSTANCE = new SceneHistoryManager();
    private static final int MAX_HISTORY = 100;

    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();
    private HistoryEntry pendingContinuous;

    private SceneHistoryManager() {
    }

    public static SceneHistoryManager getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> snapshot(SceneObject object) {
        if (object == null) {
            return new ConcurrentHashMap<>();
        }
        return new ConcurrentHashMap<>(object.getProperties());
    }

    public void beginContinuousChange(SceneObject object) {
        if (object == null) {
            return;
        }
        pendingContinuous = new HistoryEntry(object.getId(), snapshot(object), snapshot(object));
        redoStack.clear();
    }

    public void updateContinuousChange(SceneObject object) {
        if (pendingContinuous == null || object == null) {
            return;
        }
        if (!Objects.equals(pendingContinuous.objectId, object.getId())) {
            return;
        }
        pendingContinuous.after = snapshot(object);
    }

    public void commitContinuousChange() {
        if (pendingContinuous == null) {
            return;
        }
        if (!pendingContinuous.before.equals(pendingContinuous.after)) {
            pushEntry(pendingContinuous);
        }
        pendingContinuous = null;
    }

    public void flushPendingChange() {
        pendingContinuous = null;
    }

    public void recordDiscreteChange(String objectId, Map<String, Object> before, Map<String, Object> after) {
        if (objectId == null || before == null || after == null) {
            return;
        }
        if (before.equals(after)) {
            return;
        }
        redoStack.clear();
        pushEntry(new HistoryEntry(objectId, new ConcurrentHashMap<>(before), new ConcurrentHashMap<>(after)));
    }

    public void undo() {
        if (pendingContinuous != null) {
            commitContinuousChange();
        }
        HistoryEntry entry = undoStack.pollFirst();
        if (entry == null) {
            return;
        }
        applyState(entry.objectId, entry.before);
        redoStack.offerFirst(entry);
    }

    public void redo() {
        if (pendingContinuous != null) {
            commitContinuousChange();
        }
        HistoryEntry entry = redoStack.pollFirst();
        if (entry == null) {
            return;
        }
        applyState(entry.objectId, entry.after);
        undoStack.offerFirst(entry);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        pendingContinuous = null;
    }

    public void dropEntriesForObject(String objectId) {
        if (objectId == null) {
            return;
        }
        undoStack.removeIf(entry -> entry.objectId.equals(objectId));
        redoStack.removeIf(entry -> entry.objectId.equals(objectId));
        if (pendingContinuous != null && pendingContinuous.objectId.equals(objectId)) {
            pendingContinuous = null;
        }
    }

    private void pushEntry(HistoryEntry entry) {
        undoStack.offerFirst(entry.copy());
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.pollLast();
        }
    }

    private void applyState(String objectId, Map<String, Object> properties) {
        if (objectId == null || properties == null) {
            return;
        }
        SceneSessionManager.getInstance().submitFullProperties(objectId, new ConcurrentHashMap<>(properties));
    }

    private static final class HistoryEntry {
        private final String objectId;
        private final Map<String, Object> before;
        private Map<String, Object> after;

        private HistoryEntry(String objectId, Map<String, Object> before, Map<String, Object> after) {
            this.objectId = objectId;
            this.before = before;
            this.after = after;
        }

        private HistoryEntry copy() {
            return new HistoryEntry(
                    objectId,
                    new ConcurrentHashMap<>(before),
                    new ConcurrentHashMap<>(after)
            );
        }
    }
}
