package com.moud.client.mixin;

import com.moud.client.imgui.VeilDataAccessor;
import imgui.glfw.ImGuiImplGlfw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
@Mixin(value = foundry.veil.impl.client.imgui.VeilImGuiImplGlfw.class, remap = false)
public abstract class VeilImGuiGlfwMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoudImGuiCompat");

    @Inject(method = "newData", at = @At("RETURN"), cancellable = true)
    private void moud$attachOwner(CallbackInfoReturnable<Object> cir) {
        Object data = cir.getReturnValue();
        if (data instanceof VeilDataAccessor accessor) {
            accessor.moud$setOwner((ImGuiImplGlfw) (Object) this);
        }
    }

    public void shutdown() {
        try {
            Method dispose = ImGuiImplGlfw.class.getMethod("dispose");
            dispose.setAccessible(true);
            dispose.invoke(this);
        } catch (NoSuchMethodException ignored) {
            LOGGER.debug("ImGui dispose() not present; skipping shutdown bridge");
        } catch (Throwable t) {
            LOGGER.debug("ImGui dispose() threw during shutdown bridge", t);
        }
    }
}
