package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.Launch;
import com.meekdev.moud.mod.client.editor.notify.ToastCenter;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorScaling;
import com.meekdev.moud.mod.client.editor.style.IconAtlas;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImFont;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ProjectHub {

    private final ToastCenter toasts = new ToastCenter();
    private final IconWidgets icons = new IconWidgets(new IconAtlas());
    private @Nullable ProjectHubView view;
    private @Nullable Project pending;

    public void tick() {
        Project project = pending;
        if (project == null) return;
        pending = null;
        MoudMod.LOG.info("opening project {} at {}", project.name(), project.rootDirectory());
        Launch.openProject(project.rootDirectory());
    }

    public void render() {
        if (!(Minecraft.getInstance().screen instanceof ProjectHubScreen)) {
            if (view != null) {
                view.dispose();
                view = null;
            }
            return;
        }
        float fontScale = EditorScaling.begin();
        HubStyle.apply();
        ImFont body = EditorFonts.page(HubStyle.BODY, false);
        if (body != null) ImGui.pushFont(body, EditorScale.of(HubStyle.BODY));
        try {
            if (view == null) {
                view = new ProjectHubView(new ProjectStore(ProjectStore.defaultRecentsFile()), toasts, icons, project -> pending = project);
            }
            view.render();
            toasts.render();
        } catch (RuntimeException e) {
            MoudMod.LOG.error("project hub frame failed", e);
        } finally {
            if (body != null) ImGui.popFont();
            EditorScaling.end(fontScale);
        }
    }

}
