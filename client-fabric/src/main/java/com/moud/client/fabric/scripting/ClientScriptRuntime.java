package com.moud.client.fabric.scripting;

import com.moud.client.fabric.assets.MoudTextAssets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import com.moud.client.fabric.runtime.CharacterBody3D;
import com.moud.client.fabric.runtime.ClientCameraState;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scripting.api.AnimApi;
import com.moud.client.fabric.scripting.api.BodyApi;
import com.moud.client.fabric.scripting.api.CameraApi;
import com.moud.client.fabric.scripting.api.InputApi;
import com.moud.client.fabric.scripting.api.MessagingApi;
import com.moud.client.fabric.scripting.api.NetApi;
import com.moud.client.fabric.scripting.api.MouseApi;
import com.moud.client.fabric.scripting.api.NodeApi;
import com.moud.client.fabric.scripting.api.PlayerStateApi;
import com.moud.client.fabric.scripting.api.PostProcessApi;
import com.moud.client.fabric.scripting.api.RenderApi;
import com.moud.client.fabric.scripting.api.TimerApi;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.SceneSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientScriptRuntime {

    private static final String TAG = "ClientScriptRuntime";

    private final ClientLuauBridge bridge = new ClientLuauBridge();

    private final Map<Long, ActiveEntry> contexts = new HashMap<>();


    public void syncAllNodes(CharacterBody3D characterBody,
                             InputApi.InputStateSnapshot inputSnapshot,
                             ClientCameraState cameraState) {
        if (!ClientLuauBridge.isRuntimeLinked()) {
            return;
        }

        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        Set<Long> activeNodeIds = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            String scriptPath = clientScriptProperty(node);
            if (scriptPath == null || scriptPath.isBlank()) continue;

            long nodeId = node.nodeId();
            activeNodeIds.add(nodeId);

            String source = MoudTextAssets.readText(scriptPath);
            if (source == null) continue;

            ActiveEntry existing = contexts.get(nodeId);

            if (existing != null
                    && scriptPath.equals(existing.scriptPath())
                    && source.hashCode() == existing.sourceHash()) {
                if (existing.inputApi() != null) {
                    existing.inputApi().updateEdges(inputSnapshot);
                }
                continue;
            }

            if (existing != null) {
                closeEntry(nodeId, existing);
                contexts.remove(nodeId);
            }

            ClientLuauBridge.Program program = bridge.compileSource(scriptPath, source);
            if (program == null) continue;

            boolean isCharacterBody = "CharacterBody3D".equals(node.type())
                    && characterBody.isActive()
                    && characterBody.nodeId() == nodeId;

            BodyApi bodyApi   = isCharacterBody ? new BodyApi(characterBody) : new BodyApi(null);
            InputApi inputApi = new InputApi();
            inputApi.updateEdges(inputSnapshot);
            TimerApi timerApi = new TimerApi();
            MinecraftClient mc = MinecraftClient.getInstance();
            AbstractClientPlayerEntity localPlayer = mc != null ? mc.player : null;
            AnimApi    animApi    = new AnimApi(localPlayer);
            RenderApi  renderApi  = new RenderApi();
            NodeApi    nodeApi    = new NodeApi(nodeId);
            CameraApi  cameraApi  = new CameraApi(cameraState);
            MouseApi   mouseApi   = new MouseApi();
            PlayerStateApi playerStateApi = new PlayerStateApi();
            PostProcessApi postProcessApi = new PostProcessApi();
            MessagingApi messagingApi = new MessagingApi(nodeId);
            ClientScriptMessageDispatcher.register(nodeId, messagingApi);
            NetApi netApi = new NetApi(messagingApi);
            nodeApi.attachNet(netApi);

            try {
                ClientScriptContext ctx = new ClientScriptContext(
                        bridge, program, bodyApi, inputApi, timerApi, animApi, renderApi, nodeApi, cameraApi, mouseApi, playerStateApi, postProcessApi, messagingApi);
                contexts.put(nodeId, new ActiveEntry(scriptPath, source.hashCode(), program, ctx, inputApi, timerApi, postProcessApi, netApi));
                ClientDebugLog.info(TAG, "Loaded client script for node " + nodeId + ": " + scriptPath);
            } catch (Exception e) {
                ClientDebugLog.error(TAG, "Failed to create script context for node " + nodeId
                        + " (" + scriptPath + "): " + e.getMessage(), e);
            }
        }

        contexts.entrySet().removeIf(entry -> {
            if (!activeNodeIds.contains(entry.getKey())) {
                closeEntry(entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });
    }


    public void frame(double dt) {
        for (Map.Entry<Long, ActiveEntry> e : contexts.entrySet()) {
            ActiveEntry entry = e.getValue();
            if (entry == null || entry.context() == null) continue;
            if (entry.timerApi() != null) entry.timerApi().tick(dt);
            try {
                entry.context().frame(dt);
            } catch (Exception ex) {
                ClientDebugLog.error(TAG, "Script frame error for node " + e.getKey()
                        + ": " + ex.getMessage(), ex);
            }
        }
    }


    public void unload(long nodeId) {
        ActiveEntry entry = contexts.remove(nodeId);
        closeEntry(nodeId, entry);
    }

    public void unloadAll() {
        for (Map.Entry<Long, ActiveEntry> e : contexts.entrySet()) {
            closeEntry(e.getKey(), e.getValue());
        }
        contexts.clear();
    }


    private void closeEntry(long nodeId, ActiveEntry entry) {
        ClientScriptMessageDispatcher.unregister(nodeId);
        if (entry == null || entry.context() == null) return;
        try {
            entry.context().close();
        } catch (Exception e) {
            ClientDebugLog.error(TAG, "Error closing script context for node " + nodeId
                    + ": " + e.getMessage(), e);
        }
        if (entry.postProcessApi() != null) {
            try {
                entry.postProcessApi().disposeOwned();
            } catch (Exception e) {
                ClientDebugLog.error(TAG, "Error disposing post-process effects for node " + nodeId
                        + ": " + e.getMessage(), e);
            }
        }
        if (entry.netApi() != null) {
            try { entry.netApi().dispose(); } catch (Exception ignored) {}
        }
    }

    private static String clientScriptProperty(SceneSnapshot.NodeSnapshot node) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return null;
        for (SceneSnapshot.Property p : props) {
            if ("client_script".equals(p.key())) {
                String v = p.value();
                return (v != null && !v.isBlank()) ? v.trim() : null;
            }
        }
        return null;
    }

    private record ActiveEntry(
            String scriptPath,
            int sourceHash,
            ClientLuauBridge.Program program,
            ClientScriptContext context,
            InputApi inputApi,
            TimerApi timerApi,
            PostProcessApi postProcessApi,
            NetApi netApi) {
    }
}
