package com.moud.client.fabric.editor.panels;

import com.miry.ui.Ui;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.core.NodeTypeDef;
import com.moud.net.protocol.SceneSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class SceneNodeMenu {
    final ContextMenu nodeMenu = new ContextMenu();
    final ContextMenu addChildMenu = new ContextMenu();
    final ArrayList<ContextMenu> addChildCategoryMenus = new ArrayList<>();

    private final EditorRuntime runtime;
    private final SceneNodeOps sceneNodeOps;
    private final SceneNodeClipboard sceneNodeClipboard;
    private final SceneBranchExporter sceneBranchExporter;

    private TreeView<SceneSnapshot.NodeSnapshot> treeView;
    private Consumer<SceneSnapshot.NodeSnapshot> beginInlineRename;

    SceneNodeMenu(EditorRuntime runtime, SceneNodeOps sceneNodeOps, SceneNodeClipboard sceneNodeClipboard, SceneBranchExporter sceneBranchExporter) {
        this.runtime = runtime;
        this.sceneNodeOps = sceneNodeOps;
        this.sceneNodeClipboard = sceneNodeClipboard;
        this.sceneBranchExporter = sceneBranchExporter;
    }

    void setTreeView(TreeView<SceneSnapshot.NodeSnapshot> treeView) {
        this.treeView = treeView;
    }

    void setBeginInlineRename(Consumer<SceneSnapshot.NodeSnapshot> beginInlineRename) {
        this.beginInlineRename = beginInlineRename;
    }

    void beginInlineRename(SceneSnapshot.NodeSnapshot node) {
        if (beginInlineRename != null) {
            beginInlineRename.accept(node);
        }
    }

    void openNodeMenu(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.clear();
        nodeMenu.addItem("Add Child…", () -> sceneNodeOps.openCreateDialog(node.nodeId()));
        nodeMenu.addSeparator();
        nodeMenu.addItem("Rename", () -> beginInlineRename(node));
        Set<TreeNode<SceneSnapshot.NodeSnapshot>> multiSel = treeView != null ? treeView.selectedNodes() : Set.of();
        if (multiSel.size() > 1) {
            nodeMenu.addItem("Duplicate (" + multiSel.size() + ")", () -> sceneNodeOps.duplicateSelectedNodes());
        } else {
            nodeMenu.addItem("Duplicate", () -> sceneNodeOps.duplicateNode(node));
        }
        nodeMenu.addItem("Change Type…", () -> sceneNodeOps.openChangeTypeDialog(node));
        nodeMenu.addSeparator();
        boolean visible = SceneNodeOps.isVisible(node);
        boolean locked = SceneNodeOps.isLocked(node);
        nodeMenu.addItem(visible ? "Hide" : "Show", () -> sceneNodeOps.toggleVisible(node.nodeId(), visible));
        nodeMenu.addItem(locked ? "Unlock" : "Lock", () -> sceneNodeOps.toggleLocked(node.nodeId(), locked));
        if (node.parentId() != 0L) {
            nodeMenu.addSeparator();
            nodeMenu.addItem("Move Up", () -> sceneNodeOps.moveNode(node, -1));
            nodeMenu.addItem("Move Down", () -> sceneNodeOps.moveNode(node, 1));
            nodeMenu.addItem("Move to Root", () -> sceneNodeOps.moveToRoot(node));
            nodeMenu.addSeparator();
            nodeMenu.addItem("Queue free", () -> sceneNodeOps.queueFree(node.nodeId()));
        }
        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selectedNodes = treeView != null ? treeView.selectedNodes() : Set.of();
        if (selectedNodes.size() > 1) {
            nodeMenu.addSeparator();
            nodeMenu.addItem("Group Selection", () -> sceneNodeOps.groupSelectedNodes());
        }
        nodeMenu.addSeparator();
        nodeMenu.addItem("Attach Script…", () -> sceneNodeOps.attachScriptFromFile(node.nodeId()));
        nodeMenu.addItem("Copy Path", () -> sceneNodeOps.copyNodePath(node));
        nodeMenu.addSeparator();
        nodeMenu.addItem("Save Branch as Scene…", () -> sceneBranchExporter.openSaveBranch(node));
    }

    void buildAddChildMenu(SceneSnapshot.NodeSnapshot parent) {
        addChildMenu.clear();
        closeAddChildCategoryMenus();
        addChildCategoryMenus.clear();
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        long parentId = parent == null ? 0L : parent.nodeId();
        LinkedHashMap<String, ArrayList<NodeTypeDef>> categories = new LinkedHashMap<>();
        for (String typeId : state.typeIds) {
            if (typeId == null || typeId.isBlank() || "Root".equals(typeId)) {
                continue;
            }
            NodeTypeDef def = state.typesById.get(typeId);
            String category = def == null ? "" : def.category();
            String key = (category == null || category.isBlank()) ? "Other" : category.trim();
            categories.computeIfAbsent(key, ignored -> new ArrayList<>()).add(def != null ? def : new NodeTypeDef(typeId, Map.of()));
        }

        ArrayList<String> categoryNames = new ArrayList<>(categories.keySet());
        categoryNames.sort((a, b) -> {
            String aa = a == null ? "" : a;
            String bb = b == null ? "" : b;
            if ("Other".equalsIgnoreCase(aa) && !"Other".equalsIgnoreCase(bb)) {
                return 1;
            }
            if (!"Other".equalsIgnoreCase(aa) && "Other".equalsIgnoreCase(bb)) {
                return -1;
            }
            return aa.compareToIgnoreCase(bb);
        });

        for (String category : categoryNames) {
            ArrayList<NodeTypeDef> defs = categories.get(category);
            if (defs == null || defs.isEmpty()) {
                continue;
            }
            ContextMenu catMenu = new ContextMenu();
            for (NodeTypeDef def : defs) {
                if (def == null || def.typeId() == null || def.typeId().isBlank()) {
                    continue;
                }
                String finalType = def.typeId();
                catMenu.addItem(def.uiLabel(), () -> {
                    sceneNodeOps.createChildNode(parentId, finalType);
                    closeAddChildCategoryMenus();
                    addChildMenu.close();
                    nodeMenu.close();
                });
            }
            addChildCategoryMenus.add(catMenu);
            addChildMenu.addSubmenu(category, catMenu);
        }
    }

    void closeAddChildCategoryMenus() {
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu != null) {
                menu.close();
            }
        }
    }

    static boolean inside(ContextMenu menu, int mx, int my, int itemHeight) {
        if (menu == null || !menu.isOpen()) {
            return false;
        }
        int x = menu.x();
        int y = menu.y();
        int w = menu.lastWidth() > 0 ? menu.lastWidth() : 200;
        int itemH = Math.max(1, itemHeight);
        int h = menu.lastHeight() > 0 ? menu.lastHeight() : menu.items().size() * itemH;
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    void syncSubmenus(Ui ui, Theme theme, int itemH) {
        if (runtime != null && runtime.uiBlocked()) {
            return;
        }
        if (ui == null || ui.input() == null || theme == null) {
            return;
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;

        boolean nodeHoverSubmenu = false;
        int hover = nodeMenu.hoverIndex();
        if (hover >= 0 && hover < nodeMenu.items().size()) {
            ContextMenu.MenuItem item = nodeMenu.items().get(hover);
            if (item != null && item.submenu() != null) {
                nodeHoverSubmenu = true;
                ContextMenu submenu = item.submenu();
                int sx = nodeMenu.x() + nodeMenu.lastWidth() - 2;
                int sy = nodeMenu.y() + hover * itemH;
                EditorUiUtil.openMenuClamped(submenu, runtime, sx, sy);
                submenu.updateFromInput(ui.input(), theme, itemH);
                EditorUiUtil.clampOpenMenuToScreen(submenu, runtime);
            }
        }

        boolean insideAddChild = addChildMenu.isOpen() && inside(addChildMenu, mx, my, itemH);
        boolean insideCategory = false;
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu != null && menu.isOpen() && inside(menu, mx, my, itemH)) {
                insideCategory = true;
                break;
            }
        }
        if (!nodeHoverSubmenu && !insideAddChild && !insideCategory) {
            closeAddChildCategoryMenus();
            addChildMenu.close();
            return;
        }

        if (addChildMenu.isOpen()) {
            if (insideAddChild) {
                addChildMenu.updateFromInput(ui.input(), theme, itemH);
                EditorUiUtil.clampOpenMenuToScreen(addChildMenu, runtime);
            }

            int catHover = addChildMenu.hoverIndex();
            boolean hoveringCategory = false;
            if (catHover >= 0 && catHover < addChildMenu.items().size()) {
                ContextMenu.MenuItem item = addChildMenu.items().get(catHover);
                if (item != null && item.submenu() != null) {
                    hoveringCategory = true;
                    ContextMenu submenu = item.submenu();
                    int sx = addChildMenu.x() + addChildMenu.lastWidth() - 2;
                    int sy = addChildMenu.y() + catHover * itemH;
                    EditorUiUtil.openMenuClamped(submenu, runtime, sx, sy);
                    submenu.updateFromInput(ui.input(), theme, itemH);
                    EditorUiUtil.clampOpenMenuToScreen(submenu, runtime);
                }
            }
            if (!hoveringCategory && !insideCategory) {
                closeAddChildCategoryMenus();
            }
        }
    }

    void renderSubmenus(UiRenderer r, Theme theme, int itemH) {
        if (r == null || theme == null) {
            return;
        }
        int menuBg = Theme.darkenArgb(Theme.toArgb(theme.panelBg), 0.06f);
        int menuHover = Theme.mulAlpha(Theme.toArgb(theme.accent), 0.22f);
        int menuText = Theme.toArgb(theme.text);
        if (addChildMenu.isOpen()) {
            addChildMenu.render(r, theme, itemH, menuBg, menuHover, menuText, addChildMenu.hoverIndex());
        }
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu == null || !menu.isOpen()) {
                continue;
            }
            menu.render(r, theme, itemH, menuBg, menuHover, menuText, menu.hoverIndex());
        }
    }

    boolean handleSubmenuClick(Ui ui, int itemH) {
        if (ui == null || ui.input() == null) {
            return false;
        }
        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;

        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu == null || !menu.isOpen()) {
                continue;
            }
            if (!inside(menu, mx, my, itemH)) {
                continue;
            }
            boolean handled = menu.handleClick(mx, my, itemH);
            if (handled) {
                nodeMenu.close();
                addChildMenu.close();
                closeAddChildCategoryMenus();
            }
            return handled;
        }

        if (!addChildMenu.isOpen() || !inside(addChildMenu, mx, my, itemH)) {
            return false;
        }
        boolean handled = addChildMenu.handleClick(mx, my, itemH);
        if (handled) {
            nodeMenu.close();
            addChildMenu.close();
            closeAddChildCategoryMenus();
        }
        return handled;
    }
}
