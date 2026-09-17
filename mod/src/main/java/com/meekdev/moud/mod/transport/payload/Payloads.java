package com.meekdev.moud.mod.transport.payload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Payloads {

    private Payloads() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("moud", path));
    }

    public static void register() {
        PayloadTypeRegistry<RegistryFriendlyByteBuf> down = PayloadTypeRegistry.clientboundPlay();
        down.registerLarge(DeltaPayload.TYPE, DeltaPayload.CODEC.cast(), DeltaPayload.MAX_BYTES);
        down.register(RemoteDownPayload.TYPE, RemoteDownPayload.CODEC.cast());
        down.register(CallDownPayload.TYPE, CallDownPayload.CODEC.cast());
        down.register(ChatDownPayload.TYPE, ChatDownPayload.CODEC.cast());
        down.register(DebugPayload.TYPE, DebugPayload.CODEC.cast());
        down.register(PilotDownPayload.TYPE, PilotDownPayload.CODEC.cast());
        down.register(EditDownPayload.TYPE, EditDownPayload.CODEC.cast());
        down.register(PlaceReloadedPayload.TYPE, PlaceReloadedPayload.CODEC.cast());
        down.register(ControlsPayload.TYPE, ControlsPayload.CODEC.cast());
        down.register(PushPayload.TYPE, PushPayload.CODEC.cast());
        down.register(SceneStatusPayload.TYPE, SceneStatusPayload.CODEC.cast());
        down.register(ScenePastedPayload.TYPE, ScenePastedPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(RemoteUpPayload.TYPE, RemoteUpPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(CallUpPayload.TYPE, CallUpPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ChatUpPayload.TYPE, ChatUpPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ToolPayload.TYPE, ToolPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(PromptPayload.TYPE, PromptPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ClickPayload.TYPE, ClickPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(PilotUpPayload.TYPE, PilotUpPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ResyncPayload.TYPE, ResyncPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(EditUpPayload.TYPE, EditUpPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(SceneEditPayload.TYPE, SceneEditPayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ScenePastePayload.TYPE, ScenePastePayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(SceneSavePayload.TYPE, SceneSavePayload.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(SceneFilePayload.TYPE, SceneFilePayload.CODEC.cast());
    }
}
