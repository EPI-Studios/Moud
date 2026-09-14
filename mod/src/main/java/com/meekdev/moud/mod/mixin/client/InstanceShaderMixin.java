package com.meekdev.moud.mod.mixin.client;

import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.moud.mod.adapter.render.ShaderPatches;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.meekdev.amnetic.client.instanced.internal.CompiledShader", remap = false)
abstract class InstanceShaderMixin {

    @Inject(method = "loadSource", at = @At("RETURN"), cancellable = true)
    private static void moud$patch(Identifier id, CallbackInfoReturnable<String> cir) {
        ShaderPatches.loading(id.toString());
        cir.setReturnValue(ShaderPatches.source(id.toString(), id.getPath().endsWith(".vsh"), cir.getReturnValue()));
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void moud$linked(InstancedMesh<?> mesh, CallbackInfoReturnable<Object> cir) {
        Object shader = cir.getReturnValue();
        try {
            var program = shader.getClass().getDeclaredMethod("programId");
            program.setAccessible(true);
            ShaderPatches.linkedLoaded((int) program.invoke(shader));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
