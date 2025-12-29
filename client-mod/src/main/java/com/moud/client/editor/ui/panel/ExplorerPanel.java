package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.ProjectFileIndex;
import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.*;


public final class ExplorerPanel {
    private final SceneEditorOverlay overlay;
    private final ImString hierarchyFilter = new ImString(64);
    private final ImString runtimeFilter = new ImString(64);
    private final ImString projectFileFilter = new ImString(64);

    public ExplorerPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.EXPLORER, "Explorer")) {
            ImGui.end();
            return;
        }
        renderToolbar(session);
        ImGui.separator();
        if (ImGui.beginTabBar("explorer-tabs")) {
            if (ImGui.beginTabItem("Scene")) {
                renderSceneTab(session);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Files")) {
                renderFilesTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Runtime")) {
                renderRuntimeTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }

    private void renderToolbar(SceneSessionManager session) {
        if (ImGui.button("Refresh Scene")) {
            session.forceRefresh();
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh Project Files")) {
            ProjectFileIndex.getInstance().forceRefresh();
        }
        ImGui.sameLine();
        ImGui.textDisabled("Drag assets/files into the tree to parent entities.");
    }

    private void renderSceneTab(SceneSessionManager session) {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##hierarchy_search", "Search scene...", hierarchyFilter);
        ImGui.beginChild("hierarchy-scroll", 0, ImGui.getContentRegionAvailY(), true);

        Map<String, List<SceneObject>> tree = overlay.getCachedHierarchy(session);
        List<SceneObject> roots = tree.getOrDefault("", List.of());
        String filter = hierarchyFilter.get().trim().toLowerCase(Locale.ROOT);

        Map<String, List<SceneObject>> groupedObjects = groupObjectsByType(roots, tree);
        renderObjectGroup("Models", "model", groupedObjects, session, tree, filter);
        renderObjectGroup("Primitives", "primitive", groupedObjects, session, tree, filter);
        renderObjectGroup("Lights", "light", groupedObjects, session, tree, filter);
        renderObjectGroup("Markers", "marker", groupedObjects, session, tree, filter);
        renderObjectGroup("Cameras", "camera", groupedObjects, session, tree, filter);
        renderObjectGroup("Zones", "zone", groupedObjects, session, tree, filter);
        renderObjectGroup("Player Models", "playermodel", groupedObjects, session, tree, filter);
        renderObjectGroup("Displays", "display", groupedObjects, session, tree, filter);
        renderObjectGroup("Groups", "group", groupedObjects, session, tree, filter);
        renderObjectGroup("Other", "other", groupedObjects, session, tree, filter);

        if (ImGui.beginDragDropTarget()) {
            overlay.handleHierarchyDrop(null);
            ImGui.endDragDropTarget();
        }
        ImGui.endChild();
    }

    private Map<String, List<SceneObject>> groupObjectsByType(List<SceneObject> roots, Map<String, List<SceneObject>> tree) {
        Map<String, List<SceneObject>> groups = new HashMap<>();
        for (SceneObject root : roots) {
            collectObjectsByType(root, tree, groups);
        }
        return groups;
    }

    private void collectObjectsByType(SceneObject obj, Map<String, List<SceneObject>> tree, Map<String, List<SceneObject>> groups) {
        String type = obj.getType() == null ? "other" : obj.getType().toLowerCase(Locale.ROOT);
        groups.computeIfAbsent(type, k -> new ArrayList<>()).add(obj);

        List<SceneObject> children = tree.getOrDefault(obj.getId(), List.of());
        for (SceneObject child : children) {
            collectObjectsByType(child, tree, groups);
        }
    }

    private void renderObjectGroup(String groupName, String typeKey, Map<String, List<SceneObject>> groupedObjects,
                                     SceneSessionManager session, Map<String, List<SceneObject>> tree, String filter) {
        List<SceneObject> objects = groupedObjects.getOrDefault(typeKey, List.of());

        if (objects.isEmpty()) {
            return;
        }

        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        String label = String.format("%s (%d)", groupName, objects.size());

        boolean open = ImGui.treeNodeEx("group_" + typeKey, flags, label);
        if (open) {
            Map<String, String> displayNames = buildDisplayNames(objects);

            for (SceneObject obj : objects) {
                renderGroupedObject(obj, displayNames.get(obj.getId()), session, tree, filter);
            }
            ImGui.treePop();
        }
    }


    private Map<String, String> buildDisplayNames(List<SceneObject> objects) {
        Map<String, Integer> nameCounts = new HashMap<>();
        Map<String, String> baseNames = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();

        for (SceneObject obj : objects) {
            String baseName = extractReadableName(obj);
            baseNames.put(obj.getId(), baseName);
            nameCounts.put(baseName, nameCounts.getOrDefault(baseName, 0) + 1);
        }

        Map<String, Integer> usedCounts = new HashMap<>();
        for (SceneObject obj : objects) {
            String baseName = baseNames.get(obj.getId());
            int totalCount = nameCounts.get(baseName);

            if (totalCount == 1) {
                displayNames.put(obj.getId(), baseName);
            } else {
                int count = usedCounts.getOrDefault(baseName, 0) + 1;
                usedCounts.put(baseName, count);
                displayNames.put(obj.getId(), baseName + " (" + count + ")");
            }
        }

        return displayNames;
    }

    private String extractReadableName(SceneObject obj) {
        Object label = obj.getProperties().get("label");
        if (label instanceof String && !((String) label).isEmpty()) {
            return (String) label;
        }

        Object modelPath = obj.getProperties().get("modelPath");
        if (modelPath instanceof String) {
            String path = (String) modelPath;
            String name = extractFileNameFromPath(path);
            if (name != null) {
                return capitalize(name);
            }
        }

        String type = obj.getType();
        if (type != null && !type.isEmpty()) {
            return capitalize(type);
        }

        String id = obj.getId();
        if (id.length() > 12) {
            return id.substring(0, 12) + "...";
        }
        return id;
    }


    private String extractFileNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
        int lastDot = path.lastIndexOf('.');

        if (lastSlash >= 0 && lastDot > lastSlash) {
            return path.substring(lastSlash + 1, lastDot);
        } else if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }

        return null;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    private void renderGroupedObject(SceneObject obj, String displayName, SceneSessionManager session,
                                       Map<String, List<SceneObject>> tree, String filter) {
        if (!filter.isEmpty() && !matchesFilter(obj, displayName, filter)) {
            return;
        }

        List<SceneObject> children = tree.getOrDefault(obj.getId(), List.of());
        boolean hasChildren = !children.isEmpty();

        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.FramePadding;
        if (!hasChildren) {
            flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        }
        if (overlay.isSelected(obj.getId())) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        String icon = getObjectIcon(obj.getType());
        String visibleLabel = String.format("%s %s##%s", icon, displayName, obj.getId());

        boolean open = ImGui.treeNodeEx(obj.getId(), flags, visibleLabel);

        if (ImGui.isItemClicked()) {
            boolean shiftHeld = ImGui.getIO().getKeyShift();
            overlay.selectObject(obj, shiftHeld);
        }

        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            overlay.focusOnObject(obj);
        }

        if (ImGui.beginPopupContextItem("obj_ctx_" + obj.getId())) {
            overlay.renderObjectContextMenu(obj, session);
            ImGui.endPopup();
        }

        overlay.emitHierarchyDragPayload(obj, displayName);

        if (hasChildren && open) {
            Map<String, String> childDisplayNames = buildDisplayNames(children);
            for (SceneObject child : children) {
                renderGroupedObject(child, childDisplayNames.get(child.getId()), session, tree, filter);
            }
            ImGui.treePop();
        }
    }


    private String getObjectIcon(String type) {
        if (type == null) {
            return "[ ]";
        }
        return type;
    }


    private boolean matchesFilter(SceneObject obj, String displayName, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String lowerFilter = filter.toLowerCase(Locale.ROOT);
        return displayName.toLowerCase(Locale.ROOT).contains(lowerFilter) ||
               obj.getId().toLowerCase(Locale.ROOT).contains(lowerFilter) ||
               (obj.getType() != null && obj.getType().toLowerCase(Locale.ROOT).contains(lowerFilter));
    }

    private void renderFilesTab() {
        ProjectFileIndex index = ProjectFileIndex.getInstance();
        index.requestSyncIfNeeded();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##project_file_search", "Search project files...", projectFileFilter);
        ImGui.beginChild("project-map-scroll", 0, ImGui.getContentRegionAvailY(), true);
        List<ProjectFileIndex.Node> roots = index.getRoots();
        if (roots.isEmpty()) {
            ImGui.textWrapped("Waiting for the server to stream /assets and /src. Trigger a project scan once available.");
        } else {
            String filter = projectFileFilter.get().toLowerCase(Locale.ROOT);
            for (ProjectFileIndex.Node node : roots) {
                renderProjectNode(node, filter);
            }
        }
        ImGui.endChild();
    }

    private void renderProjectNode(ProjectFileIndex.Node node, String filter) {
        boolean matches = filter.isBlank() || node.name().toLowerCase(Locale.ROOT).contains(filter);
        if (node.isDirectory()) {
            int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
            boolean open = ImGui.treeNodeEx(node.path(), flags, node.name());
            if (open) {
                for (ProjectFileIndex.Node child : node.children()) {
                    renderProjectNode(child, filter);
                }
                ImGui.treePop();
            }
        } else if (matches) {
            if (ImGui.selectable(node.name() + "##proj_" + node.path())) {
                SceneEditorDiagnostics.log("Selected file " + node.path());
            }
            overlay.emitProjectFileDragPayload(node.path());
        }
    }

    private void renderRuntimeTab() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##runtime_search", "Search runtime objects...", runtimeFilter);
        ImGui.beginChild("runtime-scroll", 0, ImGui.getContentRegionAvailY(), true);
        overlay.renderRuntimeList(runtimeFilter.get());
        ImGui.endChild();
    }
}
