package com.moud.client.fabric.mixin;

import com.moud.client.fabric.MoudServerDetector;
import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.client.fabric.render.loading.PlayLoadingOverlay;
import com.moud.client.fabric.util.ClientDebugLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMoudOverlayMixin extends Screen {
    private boolean moud$detected;
    private boolean moud$pingStarted;
    private MultiplayerServerListPinger moud$pinger;

    protected ConnectScreenMoudOverlayMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void moud$detectOnInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = mc != null ? mc.getCurrentServerEntry() : null;
        if (info == null) info = MoudServerDetector.lastConnectInfo();
        String rawFallback = MoudServerDetector.lastConnectRawAddress();
        moud$detected = MoudServerDetector.isMoud(info)
                || MoudServerDetector.isMoud(rawFallback);
        ClientDebugLog.info("MoudConnect", "init detected=" + moud$detected
                + " address=" + (info == null ? rawFallback : info.address));
        if (!moud$detected && info != null) {
            moud$startProbe(info);
        }
    }

    private void moud$startProbe(ServerInfo info) {
        if (moud$pingStarted) return;
        moud$pingStarted = true;
        ServerInfo probe = new ServerInfo(info.name == null ? "moud-probe" : info.name,
                info.address, ServerInfo.ServerType.OTHER);
        moud$pinger = new MultiplayerServerListPinger();
        try {
            moud$pinger.add(probe, () -> {
                MoudServerDetector.scanAndMark(probe);
                ClientDebugLog.info("MoudConnect", "probe done detected=" + MoudServerDetector.isMoud(probe)
                        + " label=" + (probe.label == null ? "null" : probe.label.getString()));
            }, () -> {});
        } catch (Exception e) {
            ClientDebugLog.warn("MoudConnect", "probe failed: " + e.getMessage());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void moud$tickProbe(CallbackInfo ci) {
        if (moud$pinger != null) moud$pinger.tick();
        if (!moud$detected) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerInfo info = mc != null ? mc.getCurrentServerEntry() : null;
            if (info == null) info = MoudServerDetector.lastConnectInfo();
            moud$detected = MoudServerDetector.isMoud(info)
                    || MoudServerDetector.isMoud(MoudServerDetector.lastConnectRawAddress());
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void moud$drawMoudOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!moud$detected) return;
        if (!PlayLoading.isActive()) {
            PlayLoading.begin();
            PlayLoading.pushStatus("server", "Connecting to server");
        }
        PlayLoadingOverlay.render(context);
    }
}
