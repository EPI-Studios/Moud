package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.editor.SceneManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.minestom.server.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ScenePacketHandlers implements PacketHandlerGroup {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ScenePacketHandlers.class);

    private final ServerNetworkManager networkManager;

    public ScenePacketHandlers(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(RequestSceneStatePacket.class, this::handleSceneStateRequest);
        registry.register(SceneEditPacket.class, this::handleSceneEditRequest);
        registry.register(RequestEditorAssetsPacket.class, this::handleEditorAssetsRequest);
        registry.register(RequestProjectMapPacket.class, this::handleProjectMapRequest);
        registry.register(RequestProjectFilePacket.class, this::handleProjectFileRequest);
    }

    private boolean hasEditorPermission(Player player) {
        return PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
    }

    private void handleSceneStateRequest(Player player, RequestSceneStatePacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new SceneStatePacket(packet.sceneId(), List.of(), 0));
            return;
        }
        var snapshot = SceneManager.getInstance().createSnapshot(packet.sceneId());
        networkManager.send(player, new SceneStatePacket(
                packet.sceneId(),
                snapshot.objects(),
                snapshot.version()
        ));
    }

    private void handleSceneEditRequest(Player player, SceneEditPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new SceneEditAckPacket(
                    packet.sceneId(),
                    false,
                    "Permission denied",
                    null,
                    packet.clientVersion(),
                    null
            ));
            return;
        }
        var result = SceneManager.getInstance().applyEdit(
                packet.sceneId(), packet.action(), packet.payload(), packet.clientVersion());
        SceneEditAckPacket ack = new SceneEditAckPacket(
                packet.sceneId(),
                result.success(),
                result.message(),
                result.snapshot(),
                result.version(),
                result.objectId()
        );
        networkManager.send(player, ack);
        networkManager.broadcastExcept(ack, player);
    }

    private void handleEditorAssetsRequest(Player player, RequestEditorAssetsPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new EditorAssetListPacket(List.of()));
            return;
        }
        var assets = SceneManager.getInstance().getEditorAssets();
        networkManager.send(player, new EditorAssetListPacket(assets));
    }

    private void handleProjectMapRequest(Player player, RequestProjectMapPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new ProjectMapPacket(List.of()));
            return;
        }
        var entries = SceneManager.getInstance().getProjectFileEntries();
        networkManager.send(player, new ProjectMapPacket(entries));
    }

    private void handleProjectFileRequest(Player player, RequestProjectFilePacket packet) {
        String requestedPath = packet.path() == null ? "" : packet.path().trim();
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player,
                    new ProjectFileContentPacket(requestedPath, null, false, "Permission denied", null));
            return;
        }
        if (requestedPath.isEmpty()) {
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false, "Empty path", null));
            return;
        }

        Path projectRoot = SceneManager.getInstance().getProjectRoot();
        if (projectRoot == null) {
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false, "Project root unavailable", null));
            return;
        }

        if (requestedPath.contains("..")) {
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false, "Invalid path", null));
            return;
        }

        Path resolved = projectRoot.resolve(requestedPath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false, "Path outside project", null));
            return;
        }

        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false, "File not found", null));
            return;
        }

        try {
            long maxBytes = 256 * 1024;
            long size = Files.size(resolved);
            if (size > maxBytes) {
                networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false,
                        "File too large (" + size + " bytes)", resolved.toString()));
                return;
            }
            String content = Files.readString(resolved);
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, content, true, null, resolved.toString()));
        } catch (IOException e) {
            LOGGER.warn("Failed to read project file {}", resolved, e);
            networkManager.send(player, new ProjectFileContentPacket(requestedPath, null, false,
                    "Failed to read file", resolved.toString()));
        }
    }
}
