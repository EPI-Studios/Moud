package com.moud.client.imgui;

import imgui.glfw.ImGuiImplGlfw;

/**
 * Internal bridge that lets us associate Veil's {@code VeilData} shim with the actual ImGui GLFW backend
 * used by whichever imgui-java build is on the classpath.
 */
public interface VeilDataAccessor {
    void moud$setOwner(ImGuiImplGlfw owner);

    ImGuiImplGlfw moud$getOwner();
}
