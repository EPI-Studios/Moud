package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.render.ShaderPatches;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import java.lang.reflect.Method;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
abstract class ShaderSourceMixin {

    private static final ThreadLocal<Object[]> moud$compiling = new ThreadLocal<>();

    @Inject(method = "compileShader", at = @At("HEAD"))
    private void moud$remember(@Coerce Object key, ShaderSource source, CallbackInfoReturnable<GlShaderModule> cir) {
        try {
            Method id = key.getClass().getDeclaredMethod("id");
            Method type = key.getClass().getDeclaredMethod("type");
            id.setAccessible(true);
            type.setAccessible(true);
            moud$compiling.set(new Object[] {id.invoke(key), type.invoke(key)});
        } catch (ReflectiveOperationException | RuntimeException e) {
            moud$compiling.remove();
        }
    }

    @ModifyArg(method = "compileShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glShaderSource(ILjava/lang/String;)V"), index = 1)
    private String moud$patch(String source) {
        Object[] compiling = moud$compiling.get();
        if (compiling == null || !(compiling[0] instanceof Identifier id)) return source;
        return ShaderPatches.source(id.toString(), compiling[1] == ShaderType.VERTEX, source);
    }

    @Inject(method = "compileProgram", at = @At("RETURN"))
    private void moud$linked(RenderPipeline pipeline, ShaderSource source, CallbackInfoReturnable<GlProgram> cir) {
        GlProgram program = cir.getReturnValue();
        if (program != null && program.getProgramId() > 0) {
            ShaderPatches.linked(program.getProgramId(), pipeline.getVertexShader().toString(), pipeline.getFragmentShader().toString());
        }
    }
}
