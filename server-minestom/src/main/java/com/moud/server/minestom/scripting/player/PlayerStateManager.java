package com.moud.server.minestom.scripting.player;

import com.moud.core.scene.Node;
import com.moud.server.minestom.scripting.input.InputMap;
import com.moud.server.minestom.scripting.input.ScriptInputApi;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateManager {
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> clientStateByPlayer;
    private final ConcurrentHashMap<String, float[]> playerVelocities;
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<Long>> activeCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<float[]>> followCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<float[]>> scriptCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<boolean[]>> cursorStateByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScriptInputApi> inputApiByPlayer = new ConcurrentHashMap<>();
    private final InputMap inputMap;
    private final PlayerNetworkSink networkSink;

    public PlayerStateManager(ConcurrentHashMap<String, PlayerInputState> inputsByPlayer,
                       ConcurrentHashMap<String, ConcurrentHashMap<String, String>> clientStateByPlayer,
                       ConcurrentHashMap<String, float[]> playerVelocities,
                       InputMap inputMap,
                       PlayerNetworkSink networkSink) {
        this.inputsByPlayer = Objects.requireNonNull(inputsByPlayer, "inputsByPlayer");
        this.clientStateByPlayer = Objects.requireNonNull(clientStateByPlayer, "clientStateByPlayer");
        this.playerVelocities = Objects.requireNonNull(playerVelocities, "playerVelocities");
        this.inputMap = Objects.requireNonNull(inputMap, "inputMap");
        this.networkSink = Objects.requireNonNull(networkSink, "networkSink");
    }

    public void updatePlayerPositions(Map<String, float[]> positions) {
        playerPositions.clear();
        if (positions != null) {
            playerPositions.putAll(positions);
        }
    }

    public void updatePlayerNames(Map<String, String> names) {
        playerNames.clear();
        if (names != null) {
            playerNames.putAll(names);
        }
    }

    public PlayerInputState resolveInputFor(Node node) {
        String ownerUuid = resolveOwnerUuid(node);
        if (ownerUuid != null) {
            return inputsByPlayer.get(ownerUuid);
        }
        if (inputsByPlayer.size() == 1) {
            return inputsByPlayer.values().iterator().next();
        }
        PlayerInputState best = null;
        for (PlayerInputState candidate : inputsByPlayer.values()) {
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.clientTick() > best.clientTick()) {
                best = candidate;
            }
        }
        return best;
    }

    public ScriptInputApi updateInputApi(Node node) {
        if (node == null) {
            return null;
        }
        PlayerInputState state = null;
        String ownerUuid = resolveOwnerUuid(node);
        if (ownerUuid != null) {
            state = inputsByPlayer.get(ownerUuid);
        } else if (inputsByPlayer.size() == 1) {
            state = inputsByPlayer.values().iterator().next();
            ownerUuid = state == null ? null : state.playerUuid();
        }
        if (state == null || ownerUuid == null) {
            return null;
        }
        ScriptInputApi api = inputApiByPlayer.computeIfAbsent(ownerUuid, ignored -> new ScriptInputApi(inputMap));
        api.update(state);
        return api;
    }

    public InputEvent inputEventForNode(Node node) {
        PlayerInputState state = resolveInputFor(node);
        return state == null ? null : new InputEvent(state);
    }

    public Long getActiveCameraForPlayer(String playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        OwnedValue<Long> ov = activeCameraByPlayer.get(playerUuid);
        return ov == null ? null : ov.value();
    }

    public float[] getFollowCameraForPlayer(String playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        OwnedValue<float[]> ov = followCameraByPlayer.get(playerUuid);
        return ov == null ? null : ov.value();
    }

    public float[] getScriptCameraForPlayer(String playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        OwnedValue<float[]> ov = scriptCameraByPlayer.get(playerUuid);
        return ov == null ? null : ov.value();
    }

    public void setActiveCamera(long ownerNodeId, String playerUuid, long nodeId) {
        if (playerUuid == null) {
            return;
        }
        followCameraByPlayer.remove(playerUuid);
        scriptCameraByPlayer.remove(playerUuid);
        if (nodeId <= 0L) {
            activeCameraByPlayer.remove(playerUuid);
        } else {
            activeCameraByPlayer.put(playerUuid, new OwnedValue<>(ownerNodeId, nodeId));
        }
    }

    public void setFollowCamera(long ownerNodeId, String playerUuid, float[] pose) {
        if (playerUuid == null || pose == null) {
            return;
        }
        activeCameraByPlayer.remove(playerUuid);
        scriptCameraByPlayer.remove(playerUuid);
        followCameraByPlayer.put(playerUuid, new OwnedValue<>(ownerNodeId, pose));
    }

    public void setScriptCamera(long ownerNodeId, String playerUuid, float[] pose) {
        if (playerUuid == null || pose == null) {
            return;
        }
        activeCameraByPlayer.remove(playerUuid);
        followCameraByPlayer.remove(playerUuid);
        scriptCameraByPlayer.put(playerUuid, new OwnedValue<>(ownerNodeId, pose));
    }

    public void resetCamera(String playerUuid) {
        if (playerUuid == null) {
            return;
        }
        activeCameraByPlayer.remove(playerUuid);
        followCameraByPlayer.remove(playerUuid);
        scriptCameraByPlayer.remove(playerUuid);
    }

    public void setCursorState(long ownerNodeId, String playerUuid, boolean enabled, boolean osVisible) {
        if (playerUuid == null) {
            return;
        }
        cursorStateByPlayer.put(playerUuid, new OwnedValue<>(ownerNodeId, new boolean[]{enabled, osVisible}));
        networkSink.sendCursorState(playerUuid, enabled, osVisible);
    }

    public boolean isCursorModeEnabled(String playerUuid) {
        OwnedValue<boolean[]> ov = cursorStateByPlayer.get(playerUuid);
        boolean[] state = ov == null ? null : ov.value();
        return state != null && state.length > 0 && state[0];
    }

    public boolean isOsCursorVisible(String playerUuid) {
        OwnedValue<boolean[]> ov = cursorStateByPlayer.get(playerUuid);
        boolean[] state = ov == null ? null : ov.value();
        return state == null || state.length < 2 || state[1];
    }

    public double[] getCursorPosition(String playerUuid) {
        if (playerUuid == null) {
            return new double[]{0.0, 0.0};
        }
        PlayerInputState input = inputsByPlayer.get(playerUuid);
        if (input == null) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{input.cursorX(), input.cursorY()};
    }

    public void clearOverridesOwnedBy(long ownerNodeId) {
        if (ownerNodeId <= 0L) {
            return;
        }
        activeCameraByPlayer.entrySet().removeIf(e -> ownedBy(e.getValue(), ownerNodeId));
        followCameraByPlayer.entrySet().removeIf(e -> ownedBy(e.getValue(), ownerNodeId));
        scriptCameraByPlayer.entrySet().removeIf(e -> ownedBy(e.getValue(), ownerNodeId));

        ArrayList<String> clearedCursorPlayers = new ArrayList<>();
        cursorStateByPlayer.entrySet().removeIf(e -> {
            boolean owned = ownedBy(e.getValue(), ownerNodeId);
            if (owned && e.getKey() != null) {
                clearedCursorPlayers.add(e.getKey());
            }
            return owned;
        });
        for (String playerUuid : clearedCursorPlayers) {
            networkSink.sendCursorState(playerUuid, false, true);
        }
    }

    public ScriptInputApi getInputApi(String playerUuid) {
        return playerUuid == null ? null : inputApiByPlayer.get(playerUuid);
    }

    public ScriptInputApi getInputApiForNode(Node node) {
        String ownerUuid = resolveOwnerUuid(node);
        if (ownerUuid == null && inputsByPlayer.size() == 1) {
            PlayerInputState state = inputsByPlayer.values().iterator().next();
            ownerUuid = state == null ? null : state.playerUuid();
        }
        return getInputApi(ownerUuid);
    }

    public double[] getPlayerVelocity(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return new double[]{0.0, 0.0, 0.0};
        }
        float[] v = playerVelocities.get(playerUuid.trim());
        if (v == null || v.length < 3) {
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{v[0], v[1], v[2]};
    }

    public PlayerInfo[] getPlayers() {
        String[] uuids = inputsByPlayer.keySet().toArray(new String[0]);
        PlayerInfo[] result = new PlayerInfo[uuids.length];
        for (int i = 0; i < uuids.length; i++) {
            String uuid = uuids[i];
            result[i] = new PlayerInfo(uuid, playerNames.get(uuid), playerPositions.get(uuid), clientStateSnapshot(uuid));
        }
        return result;
    }

    public String getClientState(String playerUuid, String key) {
        if (playerUuid == null || key == null) {
            return "";
        }
        Map<String, String> state = clientStateByPlayer.get(playerUuid);
        if (state == null) {
            return "";
        }
        return state.getOrDefault(key, "");
    }

    public double playerCoord(Node node, int idx) {
        String uuid = resolveOwnerUuid(node);
        if (uuid == null && inputsByPlayer.size() == 1) {
            PlayerInputState state = inputsByPlayer.values().iterator().next();
            uuid = state == null ? null : state.playerUuid();
        }
        if (uuid == null) {
            return 0.0;
        }
        float[] pos = playerPositions.get(uuid);
        return pos != null && idx < pos.length ? pos[idx] : 0.0;
    }

    public String resolveOwnerUuid(Node node) {
        return node == null ? null : RuntimeScriptUtil.resolveOwnerUuid(node);
    }

    public String resolveOwnerUuidOrSinglePlayer(Node node) {
        String uuid = resolveOwnerUuid(node);
        if (uuid != null) {
            return uuid;
        }
        if (inputsByPlayer.size() == 1) {
            PlayerInputState state = inputsByPlayer.values().iterator().next();
            return state == null ? null : state.playerUuid();
        }
        return null;
    }

    private static boolean ownedBy(OwnedValue<?> value, long ownerNodeId) {
        return value != null && value.ownerNodeId() == ownerNodeId;
    }

    private Map<String, String> clientStateSnapshot(String playerUuid) {
        Map<String, String> state = clientStateByPlayer.get(playerUuid);
        return state == null ? Map.of() : Map.copyOf(state);
    }

    private record OwnedValue<T>(long ownerNodeId, T value) {
    }
}
