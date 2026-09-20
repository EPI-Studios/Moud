package com.meekdev.moud.mod.client.account;

import com.meekdev.moud.mod.client.account.ForumLink.Code;
import imgui.ImGui;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class AccountPanel {

    private static final String SITE = "https://moud.epistudios.fr/forum/link";

    private @Nullable CompletableFuture<Code> running;
    private @Nullable Code code;
    private @Nullable String problem;

    public void draw() {
        Minecraft client = Minecraft.getInstance();
        String playing = client.getUser() == null ? "nobody" : client.getUser().getName();

        ImGui.textDisabled("Playing as " + playing);
        ImGui.spacing();

        if (code != null) {
            ImGui.text("Type this on " + SITE);
            ImGui.spacing();
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 14.0f, 10.0f);
            ImGui.button(code.code());
            ImGui.popStyleVar();
            ImGui.sameLine();
            if (ImGui.button("Copy")) client.keyboardHandler.setClipboard(code.code());
            ImGui.spacing();
            ImGui.textDisabled("It lasts " + (code.expiresIn() / 60) + " minutes and works once.");
            ImGui.spacing();
            if (ImGui.button("Get another code")) {
                code = null;
                problem = null;
            }
            return;
        }

        if (running != null && !running.isDone()) {
            ImGui.textDisabled("Asking Mojang...");
            return;
        }

        if (ImGui.button("Link forum account")) begin();

        if (problem != null) {
            ImGui.spacing();
            ImGui.textWrapped(problem);
        } else {
            ImGui.spacing();
            ImGui.textDisabled("Moud proves to Mojang who you are, then gives you a code for the site.");
        }
    }

    private void begin() {
        problem = null;
        running = ForumLink.start();
        running.whenComplete((made, failure) -> {
            if (failure != null) {
                Throwable why = failure.getCause() == null ? failure : failure.getCause();
                problem = why.getMessage() == null ? "that did not work" : why.getMessage();
                return;
            }
            code = made;
        });
    }
}
