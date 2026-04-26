package com.moud.client.fabric;

import org.spongepowered.asm.mixin.Mixins;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MoudRuntimeMixinRegistrar {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private MoudRuntimeMixinRegistrar() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            Mixins.addConfiguration("moud.client.mixins.json");
        }
    }
}
