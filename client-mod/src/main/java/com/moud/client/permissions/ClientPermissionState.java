package com.moud.client.permissions;

import com.moud.client.editor.EditorModeManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientPermissionState {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudPermissions");
    private static final ClientPermissionState INSTANCE = new ClientPermissionState();

    private volatile boolean received;
    private volatile boolean op;
    private volatile boolean editor;
    private volatile boolean devUtils;

    private ClientPermissionState() {
    }

    public static ClientPermissionState getInstance() {
        return INSTANCE;
    }

    public boolean isReceived() {
        return received;
    }

    public boolean isOp() {
        return op;
    }

    public boolean canUseEditor() {
        return editor;
    }

    public boolean canUseDevUtils() {
        return devUtils;
    }

    public void handle(MoudPackets.PermissionStatePacket packet) {
        if (packet == null) {
            return;
        }
        this.received = true;
        this.op = packet.op();
        this.editor = packet.editor();
        this.devUtils = packet.devUtils();
        LOGGER.info("Permission state updated: op={}, editor={}, devUtils={}", op, editor, devUtils);

        if (!editor && EditorModeManager.getInstance().isActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (EditorModeManager.getInstance().isActive()) {
                    EditorModeManager.getInstance().setActive(false);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Editor permission revoked by server"), true);
                    }
                }
            });
        }
    }

    public void reset() {
        received = false;
        op = false;
        editor = false;
        devUtils = false;
    }
}
