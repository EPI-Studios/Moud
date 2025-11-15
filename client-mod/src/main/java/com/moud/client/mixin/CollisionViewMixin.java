package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.collision.ModelCollisionManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Mixin(CollisionView.class)
public interface CollisionViewMixin {

    @Inject(method = "getBlockCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/lang/Iterable;", at = @At("RETURN"), cancellable = true)
    private void moud$injectBlockCollisions(@Nullable Entity entity, Box box, CallbackInfoReturnable<Iterable<VoxelShape>> cir) {
        cir.setReturnValue(withModelShapes(box, cir.getReturnValue()));
    }

    @Inject(method = "getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/lang/Iterable;", at = @At("RETURN"), cancellable = true)
    private void moud$injectEntityCollisions(@Nullable Entity entity, Box box, CallbackInfoReturnable<Iterable<VoxelShape>> cir) {
        cir.setReturnValue(withModelShapes(box, cir.getReturnValue()));
    }

    private Iterable<VoxelShape> withModelShapes(Box box, Iterable<VoxelShape> vanilla) {
        if (box == null || !MoudClientMod.isOnMoudServer()) {
            return vanilla;
        }

        Object self = this;
        if (!(self instanceof World world) || !world.isClient) {
            return vanilla;
        }

        List<VoxelShape> extra = ModelCollisionManager.getInstance().collectShapes(box);
        if (extra.isEmpty()) {
            return vanilla;
        }

        if (vanilla == null) {
            return extra;
        }

        return () -> Stream.concat(
                StreamSupport.stream(vanilla.spliterator(), false),
                extra.stream()
        ).iterator();
    }
}
