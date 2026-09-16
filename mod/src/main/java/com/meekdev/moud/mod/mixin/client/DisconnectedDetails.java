package com.meekdev.moud.mod.mixin.client;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisconnectedScreen.class)
public interface DisconnectedDetails {

    @Accessor("details")
    DisconnectionDetails moud$details();
}
