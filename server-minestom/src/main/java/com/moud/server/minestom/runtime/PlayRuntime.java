package com.moud.server.minestom.runtime;

import com.moud.core.physics.CharacterPhysics;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayRuntime {
    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final float DT = 1.0f / 20.0f;

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInput> lastInputs = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeScenes = new ConcurrentHashMap<>();
    private final Map<PlayerSceneKey, RuntimeNodes> nodesByPlayerScene = new ConcurrentHashMap<>();
    private final Map<UUID, CharacterPhysics.State> physicsStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastProcessedTicks = new ConcurrentHashMap<>();

    public void onPlayerSpawn(Player player, String sceneId) {
        if (player == null) {
            return;
        }
        players.put(player.getUuid(), player);
        if (sceneId != null && !sceneId.isBlank()) {
            activeScenes.put(player.getUuid(), sceneId);
        }
    }

    public void onDisconnect(UUID uuid) {
        if (uuid == null) {
            return;
        }
        players.remove(uuid);
        lastInputs.remove(uuid);
        activeScenes.remove(uuid);
        physicsStates.remove(uuid);
        lastProcessedTicks.remove(uuid);
        nodesByPlayerScene.keySet().removeIf(k -> uuid.equals(k.playerId()));
    }

    public void onSceneChanged(UUID uuid, String sceneId) {
        if (uuid == null) {
            return;
        }
        if (sceneId == null || sceneId.isBlank()) {
            activeScenes.remove(uuid);
            return;
        }
        activeScenes.put(uuid, sceneId);
        physicsStates.remove(uuid);
    }

    public void onInput(UUID uuid, PlayerInput input) {
        if (uuid == null || input == null) {
            return;
        }
        lastInputs.put(uuid, input);
    }

    public void tick(UUID uuid, Session session, ServerScene scene) {
        if (uuid == null || session == null || scene == null) {
            return;
        }
        Player player = players.get(uuid);
        if (player == null) {
            return;
        }
        String sceneId = scene.sceneId();
        activeScenes.put(uuid, sceneId);

        ensureWorldEnvironment(scene);
        RuntimeNodes nodes = ensureNodes(scene, uuid);
        if (nodes == null) {
            return;
        }

        PlayerInput input = lastInputs.getOrDefault(uuid, new PlayerInput(0L, 0.0f, 0.0f, 0.0f, 0.0f, false, false));
        float yaw = normalizeYaw(input.yawDeg());
        float pitch = clampPitch(input.pitchDeg());

        float speed = parseFloat(nodes.character().getProperty("speed"), 6.0f);

        CharacterPhysics.State phys = physicsStates.get(uuid);
        if (phys == null) {
            float startX = parseFloat(nodes.character().getProperty("x"), 0.0f);
            float startY = parseFloat(nodes.character().getProperty("y"), CharacterPhysics.FLOOR_Y);
            float startZ = parseFloat(nodes.character().getProperty("z"), 0.0f);
            phys = CharacterPhysics.State.at(startX, startY, startZ);
        }

        phys = CharacterPhysics.simulate(phys, input.moveX(), input.moveZ(),
                yaw, speed, input.jump(), input.sprint(), DT);

        physicsStates.put(uuid, phys);
        lastProcessedTicks.put(uuid, input.clientTick());

        applyCharacterTransform(scene, nodes.character(), phys.x(), phys.y(), phys.z(), yaw);

        WorldEnvironment env = readWorldEnvironment(scene);
        session.send(Lane.STATE, new RuntimeState(
                scene.engine().ticks(),
                input.clientTick(),
                sceneId,
                phys.x(),
                phys.y(),
                phys.z(),
                phys.velX(),
                phys.velY(),
                phys.velZ(),
                phys.onFloor(),
                yaw,
                pitch,
                env.fogEnabled(),
                env.fogColor(),
                env.fogDensity()
        ));

        player.teleport(new Pos(phys.x(), phys.y(), phys.z(), yaw, pitch));
    }

    private void applyCharacterTransform(ServerScene scene, Node character, double x, double y, double z, float yaw) {
        if (scene == null || character == null) {
            return;
        }
        long nodeId = character.nodeId();
        if (nodeId <= 0L) {
            return;
        }

        ArrayList<SceneOp> ops = new ArrayList<>(4);
        ops.add(new SceneOp.SetProperty(nodeId, "x", trimFloat((float) x)));
        ops.add(new SceneOp.SetProperty(nodeId, "y", trimFloat((float) y)));
        ops.add(new SceneOp.SetProperty(nodeId, "z", trimFloat((float) z)));
        ops.add(new SceneOp.SetProperty(nodeId, "ry", trimFloat(yaw)));

        long batchId = (scene.engine().ticks() << 32) ^ (long) nodeId;
        scene.apply(new SceneOpBatch(batchId, true, List.copyOf(ops)));
    }

    private void ensureWorldEnvironment(ServerScene scene) {
        Node root = scene.engine().sceneTree().root();
        Node existing = root.findChild("WorldEnvironment");
        if (existing != null) {
            return;
        }
        PlainNode env = new PlainNode("WorldEnvironment");
        env.setProperty("@type", "WorldEnvironment");
        scene.engine().nodeTypes().applyDefaults(env, "WorldEnvironment");
        root.addChild(env);
    }

    private RuntimeNodes ensureNodes(ServerScene scene, UUID uuid) {
        PlayerSceneKey key = new PlayerSceneKey(uuid, scene.sceneId());
        RuntimeNodes cached = nodesByPlayerScene.get(key);
        if (cached != null && isValid(scene, cached)) {
            return cached;
        }

        Node root = scene.engine().sceneTree().root();
        String prefix = uuid.toString().replace("-", "");
        String bodyName = "player_" + prefix.substring(0, 8);

        Node existing = root.findChild(bodyName);
        if (existing != null) {
            Node cam = existing.findChild("Camera3D");
            if (cam != null) {
                RuntimeNodes nodes = new RuntimeNodes(existing, cam);
                nodesByPlayerScene.put(key, nodes);
                return nodes;
            }
        }

        PlainNode character = new PlainNode(bodyName);
        character.setProperty("@type", "CharacterBody3D");
        scene.engine().nodeTypes().applyDefaults(character, "CharacterBody3D");

        PlainNode camera = new PlainNode("Camera3D");
        camera.setProperty("@type", "Camera3D");
        camera.setProperty("@inherit_transform", "false");
        scene.engine().nodeTypes().applyDefaults(camera, "Camera3D");

        character.addChild(camera);
        root.addChild(character);

        RuntimeNodes nodes = new RuntimeNodes(character, camera);
        nodesByPlayerScene.put(key, nodes);
        return nodes;
    }

    private boolean isValid(ServerScene scene, RuntimeNodes nodes) {
        if (nodes == null) {
            return false;
        }
        Node c = scene.engine().sceneTree().getNode(nodes.character().nodeId());
        Node cam = scene.engine().sceneTree().getNode(nodes.camera().nodeId());
        return c == nodes.character() && cam == nodes.camera();
    }

    private WorldEnvironment readWorldEnvironment(ServerScene scene) {
        Node root = scene.engine().sceneTree().root();
        Node node = root.findChild("WorldEnvironment");
        if (node == null) {
            return new WorldEnvironment(false, "0.5,0.5,0.5", 0.02f);
        }
        boolean enabled = parseBool(node.getProperty("fog_enabled"));
        String color = defaulted(node.getProperty("fog_color"), "0.5,0.5,0.5");
        float density = parseFloat(node.getProperty("fog_density"), 0.02f);
        return new WorldEnvironment(enabled, color, density);
    }

    private static String defaulted(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static float parseFloat(String value, float fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            float v = Float.parseFloat(value.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBool(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v);
    }

    private static float clampPitch(float pitchDeg) {
        if (!Float.isFinite(pitchDeg)) {
            return 0.0f;
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitchDeg));
    }

    private static float normalizeYaw(float yawDeg) {
        if (!Float.isFinite(yawDeg)) {
            return 0.0f;
        }
        float wrapped = (float) (yawDeg % 360.0);
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static String trimFloat(float v) {
        if (!Float.isFinite(v)) {
            v = 0.0f;
        }
        if (Math.abs(v) < 1e-6f) {
            v = 0.0f;
        }
        String s = Float.toString(v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private record RuntimeNodes(Node character, Node camera) {
    }

    private record WorldEnvironment(boolean fogEnabled, String fogColor, float fogDensity) {
    }

    private record PlayerSceneKey(UUID playerId, String sceneId) {
    }
}