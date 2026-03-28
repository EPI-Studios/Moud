package com.moud.client.fabric.editor.state;

import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.scene.SceneState;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditorHistory {
    private static final int MAX_HISTORY = 50;

    public interface Entry {
        default boolean canUndo(EditorRuntime runtime) { return true; }
        default boolean canRedo(EditorRuntime runtime) { return true; }
        void undo(EditorRuntime runtime);
        void redo(EditorRuntime runtime);
    }

    public record NodeAtIndex(long nodeId, int index) {}

    private record OpsEntry(List<SceneOp> undoOps, List<SceneOp> redoOps) implements Entry {
        public OpsEntry {
            undoOps = undoOps != null ? List.copyOf(undoOps) : List.of();
            redoOps = redoOps != null ? List.copyOf(redoOps) : List.of();
        }

        @Override
        public void undo(EditorRuntime runtime) {
            sendOps(runtime, undoOps);
        }

        @Override
        public void redo(EditorRuntime runtime) {
            sendOps(runtime, redoOps);
        }
    }

    public static final class CreateNodeEntry implements Entry {
        private final long parentId;
        private final String nameHint;
        private final String typeId;
        private final List<Map.Entry<String, String>> properties;
        private final boolean selectAfterCreate;

        private long createdNodeId;
        private boolean isExecuting;

        public CreateNodeEntry(long parentId, String nameHint, String typeId,
                               List<Map.Entry<String, String>> properties,
                               boolean selectAfterCreate) {
            this.parentId = parentId;
            this.nameHint = nameHint == null ? "" : nameHint;
            this.typeId = typeId == null ? "Node" : typeId;
            this.properties = properties != null ? List.copyOf(properties) : List.of();
            this.selectAfterCreate = selectAfterCreate;
        }

        @Override
        public boolean canUndo(EditorRuntime runtime) {
            return !isExecuting && createdNodeId > 0L;
        }

        @Override
        public boolean canRedo(EditorRuntime runtime) {
            return !isExecuting;
        }

        @Override
        public void undo(EditorRuntime runtime) {
            if (!canUndo(runtime) || runtime == null) return;

            long idToRemove = createdNodeId;
            createdNodeId = 0L;
            sendOps(runtime, List.of(new SceneOp.QueueFree(idToRemove)));

            EditorState state = runtime.state();
            if (state != null && state.selectedId == idToRemove) {
                state.selectedId = 0L;
            }
        }

        @Override
        public void redo(EditorRuntime runtime) {
            if (!canRedo(runtime) || !isValidRuntime(runtime)) return;

            EditorState state = runtime.state();
            Session session = runtime.session();
            EditorNet net = runtime.net();

            String uniqueName = generateUniqueName(state, parentId, nameHint);
            List<SceneOp> createOperation = List.of(new SceneOp.CreateNode(parentId, uniqueName, typeId));

            isExecuting = true;
            long batchId = net.sendOpsWithBatchId(session, state, createOperation);

            runtime.afterCreateNode(batchId, newId -> {
                createdNodeId = newId;
                isExecuting = false;

                applyProperties(net, session, state, newId);

                if (selectAfterCreate) {
                    state.selectedId = newId;
                }
            });
        }

        private void applyProperties(EditorNet net, Session session, EditorState state, long newId) {
            if (properties.isEmpty()) return;

            List<SceneOp> propertyOperations = new ArrayList<>(properties.size());
            for (Map.Entry<String, String> property : properties) {
                if (property == null) continue;

                String key = property.getKey();
                String value = property.getValue();

                if (key == null || key.isBlank() || value == null || "@type".equals(key)) continue;

                propertyOperations.add(new SceneOp.SetProperty(newId, key, value));
            }

            if (!propertyOperations.isEmpty()) {
                net.sendOps(session, state, propertyOperations);
            }
        }
    }

    public static final class DuplicateSubtreeEntry implements Entry {
        public record CloneSpec(
                String name,
                String typeId,
                List<Map.Entry<String, String>> properties,
                List<CloneSpec> children
        ) {
            public CloneSpec {
                name = (name == null || name.isBlank()) ? "Node" : name.trim();
                typeId = (typeId == null || typeId.isBlank()) ? "Node" : typeId.trim();
                properties = properties != null ? List.copyOf(properties) : List.of();
                children = children != null ? List.copyOf(children) : List.of();
            }
        }

        private final long parentId;
        private final String rootNameHint;
        private final CloneSpec rootSpec;
        private final boolean selectAfterCreate;

        private long createdRootId;
        private boolean isExecuting;

        public DuplicateSubtreeEntry(long parentId, String rootNameHint, CloneSpec rootSpec, boolean selectAfterCreate) {
            this.parentId = parentId;
            this.rootNameHint = (rootNameHint == null || rootNameHint.isBlank()) ? "Node_copy" : rootNameHint.trim();
            this.rootSpec = rootSpec;
            this.selectAfterCreate = selectAfterCreate;
        }

        @Override
        public boolean canUndo(EditorRuntime runtime) {
            return !isExecuting && createdRootId > 0L;
        }

        @Override
        public boolean canRedo(EditorRuntime runtime) {
            return !isExecuting && rootSpec != null;
        }

        @Override
        public void undo(EditorRuntime runtime) {
            if (!canUndo(runtime) || runtime == null) {
                return;
            }

            long idToRemove = createdRootId;
            createdRootId = 0L;
            sendOps(runtime, List.of(new SceneOp.QueueFree(idToRemove)));

            EditorState state = runtime.state();
            if (state != null && state.selectedId == idToRemove) {
                state.selectedId = 0L;
            }
        }

        @Override
        public void redo(EditorRuntime runtime) {
            if (!canRedo(runtime) || !isValidRuntime(runtime)) {
                return;
            }

            EditorState state = runtime.state();
            Session session = runtime.session();
            EditorNet net = runtime.net();

            String uniqueRootName = generateUniqueName(state, parentId, rootNameHint);
            List<SceneOp> createOperation = List.of(new SceneOp.CreateNode(parentId, uniqueRootName, rootSpec.typeId()));

            isExecuting = true;
            long batchId = net.sendOpsWithBatchId(session, state, createOperation);

            runtime.afterCreateNode(batchId, newRootId -> {
                createdRootId = newRootId;

                applyProperties(net, session, state, newRootId, rootSpec.properties());
                createChildren(net, session, state, runtime, newRootId, rootSpec.children(), () -> {
                    if (selectAfterCreate) {
                        state.selectedId = newRootId;
                    }
                    isExecuting = false;
                });
            });
        }

        private static void createChildren(EditorNet net,
                                           Session session,
                                           EditorState state,
                                           EditorRuntime runtime,
                                           long parentNewId,
                                           List<CloneSpec> children,
                                           Runnable onComplete) {
            if (children == null || children.isEmpty()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            createChildAt(net, session, state, runtime, parentNewId, children, 0, onComplete);
        }

        private static void createChildAt(EditorNet net,
                                          Session session,
                                          EditorState state,
                                          EditorRuntime runtime,
                                          long parentNewId,
                                          List<CloneSpec> children,
                                          int index,
                                          Runnable onComplete) {
            if (children == null || index >= children.size()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            CloneSpec spec = children.get(index);
            if (spec == null) {
                createChildAt(net, session, state, runtime, parentNewId, children, index + 1, onComplete);
                return;
            }

            String childName = spec.name();
            List<SceneOp> createOperation = List.of(new SceneOp.CreateNode(parentNewId, childName, spec.typeId()));
            long batchId = net.sendOpsWithBatchId(session, state, createOperation);

            runtime.afterCreateNode(batchId, newChildId -> {
                applyProperties(net, session, state, newChildId, spec.properties());
                createChildren(net, session, state, runtime, newChildId, spec.children(),
                        () -> createChildAt(net, session, state, runtime, parentNewId, children, index + 1, onComplete));
            });
        }

        private static void applyProperties(EditorNet net,
                                            Session session,
                                            EditorState state,
                                            long nodeId,
                                            List<Map.Entry<String, String>> properties) {
            if (properties == null || properties.isEmpty()) {
                return;
            }

            ArrayList<SceneOp> ops = new ArrayList<>(properties.size());
            for (Map.Entry<String, String> property : properties) {
                if (property == null) {
                    continue;
                }
                String key = property.getKey();
                String value = property.getValue();
                if (key == null || key.isBlank() || value == null || "@type".equals(key)) {
                    continue;
                }
                ops.add(new SceneOp.SetProperty(nodeId, key, value));
            }

            if (!ops.isEmpty()) {
                net.sendOps(session, state, ops);
            }
        }
    }

    public static final class GroupSelectionEntry implements Entry {
        private final long parentId;
        private final String nameHint;
        private final List<NodeAtIndex> nodes;

        private long groupId;
        private boolean isExecuting;

        public GroupSelectionEntry(long parentId, String nameHint, List<NodeAtIndex> nodes) {
            this.parentId = parentId;
            this.nameHint = nameHint == null ? "Group" : nameHint;
            this.nodes = nodes != null ? List.copyOf(nodes) : List.of();
        }

        @Override
        public boolean canUndo(EditorRuntime runtime) {
            return !isExecuting && groupId > 0L;
        }

        @Override
        public boolean canRedo(EditorRuntime runtime) {
            return !isExecuting && !nodes.isEmpty();
        }

        @Override
        public void undo(EditorRuntime runtime) {
            if (!canUndo(runtime) || !isValidRuntime(runtime)) return;

            EditorState state = runtime.state();
            Session session = runtime.session();
            EditorNet net = runtime.net();

            long idToRemove = groupId;
            groupId = 0L;

            List<NodeAtIndex> sortedNodes = getSortedValidNodes();
            List<SceneOp> operations = new ArrayList<>(sortedNodes.size() + 1);

            for (NodeAtIndex node : sortedNodes) {
                operations.add(new SceneOp.Reparent(node.nodeId(), parentId, Math.max(0, node.index())));
            }
            operations.add(new SceneOp.QueueFree(idToRemove));

            net.sendOps(session, state, operations);

            if (state.selectedId == idToRemove) {
                state.selectedId = sortedNodes.isEmpty() ? 0L : sortedNodes.getFirst().nodeId();
            }
        }

        @Override
        public void redo(EditorRuntime runtime) {
            if (!canRedo(runtime) || !isValidRuntime(runtime)) return;

            EditorState state = runtime.state();
            Session session = runtime.session();
            EditorNet net = runtime.net();

            int targetIndex = calculateGroupIndex();
            String groupName = generateUniqueName(state, parentId, nameHint);

            List<SceneOp> createOperation = List.of(new SceneOp.CreateNode(parentId, groupName, "Node3D"));

            isExecuting = true;
            long batchId = net.sendOpsWithBatchId(session, state, createOperation);

            runtime.afterCreateNode(batchId, newGroupId -> {
                groupId = newGroupId;
                isExecuting = false;

                List<NodeAtIndex> sortedNodes = getSortedValidNodes();
                List<SceneOp> operations = new ArrayList<>(sortedNodes.size() + 2);

                operations.add(new SceneOp.Reparent(newGroupId, parentId, targetIndex));

                for (int i = 0; i < sortedNodes.size(); i++) {
                    operations.add(new SceneOp.Reparent(sortedNodes.get(i).nodeId(), newGroupId, i));
                }

                net.sendOps(session, state, operations);
                state.selectedId = newGroupId;
            });
        }

        private List<NodeAtIndex> getSortedValidNodes() {
            return nodes.stream()
                    .filter(node -> node != null && node.nodeId() > 0L)
                    .sorted(Comparator.comparingInt(NodeAtIndex::index))
                    .toList();
        }

        private int calculateGroupIndex() {
            int minIndex = nodes.stream()
                    .filter(node -> node != null)
                    .mapToInt(NodeAtIndex::index)
                    .min()
                    .orElse(0);
            return Math.max(0, minIndex);
        }
    }

    private final ArrayDeque<Entry> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Entry> redoStack = new ArrayDeque<>();

    public void push(List<SceneOp> undoOps, List<SceneOp> redoOps) {
        if (undoOps == null || undoOps.isEmpty()) return;
        pushEntry(new OpsEntry(undoOps, redoOps));
    }

    public void pushEntry(Entry entry) {
        if (entry == null) return;

        redoStack.clear();
        undoStack.push(entry);
        enforceHistoryLimit();
    }

    public boolean undo(EditorRuntime runtime) {
        if (runtime == null || undoStack.isEmpty()) return false;

        Entry entry = undoStack.peek();
        if (entry == null || !entry.canUndo(runtime)) return false;

        undoStack.pop();
        entry.undo(runtime);
        redoStack.push(entry);

        return true;
    }

    public boolean redo(EditorRuntime runtime) {
        if (runtime == null || redoStack.isEmpty()) return false;

        Entry entry = redoStack.peek();
        if (entry == null || !entry.canRedo(runtime)) return false;

        redoStack.pop();
        entry.redo(runtime);
        undoStack.push(entry);

        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public void clearRedo() {
        redoStack.clear();
    }

    public static List<SceneOp> buildInverseOps(SceneState scene, List<SceneOp> operations) {
        if (scene == null || operations == null || operations.isEmpty()) return List.of();

        List<SceneOp> inverseOperations = new ArrayList<>(operations.size());

        for (int i = operations.size() - 1; i >= 0; i--) {
            SceneOp operation = operations.get(i);

            if (operation instanceof SceneOp.SetProperty setProperty) {
                String previousValue = scene.getPropertyValue(setProperty.nodeId(), setProperty.key());
                if (previousValue != null) {
                    inverseOperations.add(new SceneOp.SetProperty(setProperty.nodeId(), setProperty.key(), previousValue));
                } else {
                    inverseOperations.add(new SceneOp.RemoveProperty(setProperty.nodeId(), setProperty.key()));
                }
            } else if (operation instanceof SceneOp.RemoveProperty removeProperty) {
                String previousValue = scene.getPropertyValue(removeProperty.nodeId(), removeProperty.key());
                if (previousValue != null) {
                    inverseOperations.add(new SceneOp.SetProperty(removeProperty.nodeId(), removeProperty.key(), previousValue));
                }
            } else if (operation instanceof SceneOp.Rename rename) {
                SceneSnapshot.NodeSnapshot node = scene.getNode(rename.nodeId());
                if (node != null && node.name() != null) {
                    inverseOperations.add(new SceneOp.Rename(rename.nodeId(), node.name()));
                }
            } else if (operation instanceof SceneOp.Reparent reparent) {
                SceneSnapshot.NodeSnapshot node = scene.getNode(reparent.nodeId());
                if (node != null) {
                    int previousIndex = scene.indexOfChild(node.parentId(), reparent.nodeId());
                    inverseOperations.add(new SceneOp.Reparent(reparent.nodeId(), node.parentId(), Math.max(0, previousIndex)));
                }
            }
        }

        return List.copyOf(inverseOperations);
    }

    private void enforceHistoryLimit() {
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.pollLast();
        }
    }

    private static void sendOps(EditorRuntime runtime, List<SceneOp> operations) {
        if (!isValidRuntime(runtime) || operations == null || operations.isEmpty()) return;
        runtime.net().sendOps(runtime.session(), runtime.state(), operations);
    }

    private static boolean isValidRuntime(EditorRuntime runtime) {
        return runtime != null && runtime.state() != null && runtime.session() != null && runtime.net() != null;
    }

    private static String generateUniqueName(EditorState state, long parentId, String baseName) {
        String sanitizedBase = (baseName == null || baseName.isBlank()) ? "Node" : baseName.trim();
        if (state == null || state.scene == null) return sanitizedBase;

        Set<String> existingNames = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot child : state.scene.childrenOf(parentId)) {
            if (child != null && child.name() != null) {
                existingNames.add(child.name());
            }
        }

        if (!existingNames.contains(sanitizedBase)) return sanitizedBase;

        int suffixCounter = 2;
        while (existingNames.contains(sanitizedBase + suffixCounter)) {
            suffixCounter++;
        }

        return sanitizedBase + suffixCounter;
    }
}
