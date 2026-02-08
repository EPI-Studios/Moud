package com.moud.client.mixin.accessor;

import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ResourcePackManager.class)
public interface ResourcePackManagerAccessor {
    @Accessor("providers")
    Set<ResourcePackProvider> moud$getProviders();
}
