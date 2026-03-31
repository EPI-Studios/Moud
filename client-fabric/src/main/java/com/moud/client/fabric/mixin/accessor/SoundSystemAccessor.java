package com.moud.client.fabric.mixin.accessor;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
    @Accessor("channel")
    Channel moud$getChannel();

    @Accessor("started")
    boolean moud$isStarted();
}
