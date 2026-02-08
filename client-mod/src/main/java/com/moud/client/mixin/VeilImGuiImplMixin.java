package com.moud.client.mixin;

import com.moud.client.imgui.ImGuiCompat;
import imgui.ImDrawData;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.ImPlotContext;
import imgui.gl3.ImGuiImplGl3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.util.function.ObjIntConsumer;

@Environment(EnvType.CLIENT)
@Mixin(value = foundry.veil.impl.client.imgui.VeilImGuiImpl.class, remap = false)
public abstract class VeilImGuiImplMixin {

    @Shadow @Final private ImGuiImplGl3 implGl3;

    @Shadow @Final private static MethodHandle DATA_GETTER;

    @Shadow @Final private static MethodHandle SHADER_GETTER;

    @Shadow @Final @Mutable private imgui.internal.ImGuiContext imGuiContext;

    @Shadow @Final @Mutable private ImPlotContext imPlotContext;

    @Redirect(method = "<init>(J)V", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;init(Ljava/lang/String;)Z"))
    private boolean moud$compatInit(ImGuiImplGl3 impl, String glslVersion) {
        return ImGuiCompat.init(impl, glslVersion);
    }

    @Redirect(method = "<init>(J)V", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;destroyDeviceObjects()V"))
    private void moud$compatDestroyDeviceObjectsInit(ImGuiImplGl3 impl) {
        ImGuiCompat.destroyDeviceObjects(impl);
    }

    @Redirect(method = "updateFonts", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;destroyFontsTexture()V"))
    private void moud$compatDestroyFontsTexture(ImGuiImplGl3 impl) {
        ImGuiCompat.destroyFontsTexture(impl);
    }

    @Redirect(method = "updateFonts", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;createFontsTexture()Z"))
    private boolean moud$compatCreateFontsTexture(ImGuiImplGl3 impl) {
        return ImGuiCompat.createFontsTexture(impl);
    }

    @Redirect(method = "free", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;destroyDeviceObjects()V"))
    private void moud$compatDestroyDeviceObjectsFree(ImGuiImplGl3 impl) {
        ImGuiCompat.destroyDeviceObjects(impl);
    }

    @Redirect(method = "beginFrame", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;newFrame()V"), require = 0)
    private void moud$compatNewFrame(ImGuiImplGl3 impl) {
        ImGuiCompat.newFrame(impl);
    }

    @Redirect(method = "endFrame", at = @At(value = "INVOKE", target = "Limgui/gl3/ImGuiImplGl3;renderDrawData(Limgui/ImDrawData;)V"), require = 0)
    private void moud$compatRenderDrawData(ImGuiImplGl3 impl, ImDrawData drawData) {
        ImGuiCompat.renderDrawData(impl, drawData);
    }

    @Inject(method = "addImguiShaders", at = @At("TAIL"))
    private void moud$compatAddImguiShaders(ObjIntConsumer<?> registry, CallbackInfo ci) {
        if (DATA_GETTER != null && SHADER_GETTER != null) {
            return;
        }

        int handle = ImGuiCompat.getShaderHandle(this.implGl3);
        if (handle != 0) {
            //noinspection unchecked,rawtypes
            ((ObjIntConsumer) registry).accept(Identifier.of("imgui", "blit"), handle);
        }
    }


    @Inject(method = "start", at = @At("HEAD"))
    private void moud$compatEnsureContexts(CallbackInfo ci) {
        this.imGuiContext = ImGuiCompat.ensureImGuiContext(this.imGuiContext);
        this.imPlotContext = ensureImPlotContext(this.imPlotContext);

        if (this.imGuiContext != null && !this.imGuiContext.isNotValidPtr()) {
            ImGui.setCurrentContext(this.imGuiContext);
        }
        if (this.imPlotContext != null && !this.imPlotContext.isNotValidPtr()) {
            ImPlot.setCurrentContext(this.imPlotContext);
        }
    }

    private ImPlotContext ensureImPlotContext(ImPlotContext context) {
        if (context != null && !context.isNotValidPtr()) {
            return context;
        }
        try {
            ImPlotContext current = ImPlot.getCurrentContext();
            if (current != null && !current.isNotValidPtr()) {
                return current;
            }
        } catch (Throwable ignored) {
        }
        try {
            //ImPlot.destroyContext();
        } catch (Throwable ignored) {
        }
        try {
            ImPlotContext created = ImPlot.createContext();
            if (created != null && !created.isNotValidPtr()) {
                return created;
            }
        } catch (Throwable ignored) {
        }
        return context;
    }
}
