package com.moud.client.fabric;

import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.uri.MoudUri;
import com.moud.core.uri.MoudUris;
import com.moud.net.protocol.SceneSelect;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.Locale;

final class ClientUriFlow {
    private static final long URI_TIMEOUT_MS = 30_000L;

    private final MoudClientContext ctx;
    private PendingUri pendingUri;

    ClientUriFlow(MoudClientContext ctx) {
        this.ctx = ctx;
    }

    void tick(MinecraftClient client) {
        for (String rawUri : ctx.uriServer.drain()) {
            if (rawUri == null || rawUri.isBlank()) continue;
            try {
                MoudUri link = MoudUris.parse(rawUri);
                pendingUri = new PendingUri(link, System.currentTimeMillis());
                showOverlayMessage(client, "Moud: opening " + describeUri(link));
                ClientDebugLog.info("Uri", "Accepted URI " + rawUri);
            } catch (IllegalArgumentException e) {
                ClientDebugLog.warn("Uri", "Rejected URI: " + e.getMessage());
                showOverlayMessage(client, "Moud link rejected: " + e.getMessage());
            }
        }

        PendingUri pending = pendingUri;
        if (pending == null) return;

        if (System.currentTimeMillis() - pending.receivedAtMs > URI_TIMEOUT_MS) {
            ClientDebugLog.warn("Uri", "Dropping expired URI " + pending.link.rawUri());
            showOverlayMessage(client, "Moud link timed out");
            pendingUri = null;
            return;
        }

        String targetAddress = normalizeServerAddress(pending.link.serverAddress());
        String currentAddress = currentServerAddress(client);

        if (targetAddress != null) {
            if (!targetAddress.equals(currentAddress)) {
                if (!pending.connectStarted) {
                    if (!connectToServer(client, pending.link)) {
                        pendingUri = null;
                        return;
                    }
                    pending.connectStarted = true;
                }
                return;
            }
            if (!pending.arrivedAtTargetServer) {
                pending.arrivedAtTargetServer = true;
                ClientDebugLog.info("Uri", "Arrived at target server " + targetAddress);
            }
        }

        if (!pending.link.hasSceneId() || pending.sceneSelectSent) {
            pendingUri = null;
            return;
        }

        Session currentSession = ctx.session;
        if (currentSession == null || currentSession.state() != SessionState.CONNECTED) {
            return;
        }

        currentSession.send(Lane.STATE, new SceneSelect(pending.link.sceneId()));
        pending.sceneSelectSent = true;
        showOverlayMessage(client, "Moud: joined scene " + pending.link.sceneId());
        ClientDebugLog.info("Uri", "Selected scene " + pending.link.sceneId());
        pendingUri = null;
    }

    private boolean connectToServer(MinecraftClient client, MoudUri link) {
        String rawAddress = link.serverAddress();
        if (rawAddress == null || rawAddress.isBlank()) {
            showOverlayMessage(client, "Moud link is missing a server address");
            return false;
        }
        if (!ServerAddress.isValid(rawAddress)) {
            showOverlayMessage(client, "Moud link has an invalid server address");
            ClientDebugLog.warn("Uri", "Invalid server address in URI: " + rawAddress);
            return false;
        }

        ServerAddress address = ServerAddress.parse(rawAddress);
        String displayName = link.hasSceneId() ? "Moud: " + link.sceneId() : "Moud";
        ServerInfo serverInfo = new ServerInfo(displayName, rawAddress.trim(), ServerInfo.ServerType.OTHER);
        MoudServerDetector.markAddress(rawAddress.trim());
        Screen parent = client.currentScreen;
        if (parent == null) parent = new MultiplayerScreen(new TitleScreen());

        if (client.world != null || client.getCurrentServerEntry() != null || client.getServer() != null) {
            client.disconnect(parent, false);
        }

        ConnectScreen.connect(parent, client, address, serverInfo, false, null);
        showOverlayMessage(client, "Moud: connecting to " + rawAddress);
        ClientDebugLog.info("Uri", "Connecting to " + rawAddress);
        return true;
    }

    private static String currentServerAddress(MinecraftClient client) {
        if (client == null) return null;
        ServerInfo entry = client.getCurrentServerEntry();
        return entry == null ? null : normalizeServerAddress(entry.address);
    }

    private static String normalizeServerAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || !ServerAddress.isValid(rawAddress)) {
            return null;
        }
        ServerAddress address = ServerAddress.parse(rawAddress.trim());
        String host = address.getAddress();
        if (host == null || host.isBlank()) return null;
        return host.trim().toLowerCase(Locale.ROOT) + ":" + address.getPort();
    }

    private static String describeUri(MoudUri link) {
        if (link == null) return "link";
        if (link.hasServerAddress() && link.hasSceneId()) {
            return link.serverAddress() + " / " + link.sceneId();
        }
        if (link.hasServerAddress()) return link.serverAddress();
        if (link.hasSceneId()) return link.sceneId();
        return "link";
    }

    static void showOverlayMessage(MinecraftClient client, String message) {
        if (client != null && client.inGameHud != null && message != null && !message.isBlank()) {
            client.inGameHud.setOverlayMessage(Text.literal(message), false);
        }
    }

    private static final class PendingUri {
        private final MoudUri link;
        private final long receivedAtMs;
        private boolean connectStarted;
        private boolean arrivedAtTargetServer;
        private boolean sceneSelectSent;

        private PendingUri(MoudUri link, long receivedAtMs) {
            this.link = link;
            this.receivedAtMs = receivedAtMs;
        }
    }
}
