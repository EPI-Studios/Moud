package com.moud.server.minestom;

import com.moud.core.NodeTypeDef;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.net.protocol.*;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.net.wire.WireMessages;
import com.moud.server.minestom.assets.AssetService;
import com.moud.server.minestom.assets.FileSystemAssetStore;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import com.moud.server.minestom.runtime.PlayRuntime;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomServerMain {
    private static final String CHANNEL = "moud:engine";
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

    private boolean devMode = true;
    private final Map<UUID, MinestomPlayerTransport> transports = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> schemaSent = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> scenesSent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scenesSentRevision = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeSceneId = new ConcurrentHashMap<>();
    private com.moud.server.minestom.engine.ServerScenes scenes;
    private com.moud.server.minestom.engine.ServerScene mainScene;
    private AssetService assets;
    private final PlayRuntime playRuntime = new PlayRuntime();

    public static void main(String[] args) {
        new MinestomServerMain().run();
    }

    private void run() {
        String mode = System.getenv().getOrDefault("MOUD_MODE", "dev").trim();
        devMode = !"player".equalsIgnoreCase(mode);
        System.out.println("[moud] mode=" + (devMode ? "dev" : "player"));

        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        scenes = new com.moud.server.minestom.engine.ServerScenes(instanceManager);
        mainScene = scenes.ensureDefault("main", "Main");
        scenes.create("sandbox", "Sandbox");

        try {
            assets = new AssetService(new FileSystemAssetStore(Path.of("assets")), devMode);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init asset store", e);
        }

        PlainNode demo = new PlainNode("CSGBlock_demo");
        demo.setProperty("@type", "CSGBlock");
        demo.setProperty("x", "0");
        demo.setProperty("y", "41");
        demo.setProperty("z", "5");
        demo.setProperty("sx", "1");
        demo.setProperty("sy", "6");
        demo.setProperty("sz", "1");
        demo.setProperty("block", "minecraft:diamond_block");
        mainScene.engine().sceneTree().root().addChild(demo);
        mainScene.engine().bumpSceneRevision();
        mainScene.engine().bumpCsgRevision();

        MinecraftServer.getGlobalEventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(mainScene.instance());
                    event.getPlayer().setRespawnPoint(new Pos(0, 42, 0));
                })
                .addListener(PlayerSpawnEvent.class, event -> onPlayerSpawn(event.getPlayer()))
                .addListener(PlayerPluginMessageEvent.class, this::onPluginMessage)
                .addListener(PlayerDisconnectEvent.class, event -> onDisconnect(event.getPlayer()));

        registerCommands();

        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(Duration.ofMillis(50))
                .schedule();

        MinecraftServer.getSchedulerManager()
                .buildTask(() -> System.out.println("[moud] server listening on 25565"))
                .delay(Duration.ofMillis(100))
                .schedule();

        minecraftServer.start("0.0.0.0", 25565);
    }

    private void onPlayerSpawn(Player player) {
        player.setRespawnPoint(new Pos(0, 42, 0));
        player.setGameMode(GameMode.CREATIVE);
        player.sendPluginMessage("minecraft:register", CHANNEL.getBytes(StandardCharsets.UTF_8));
        transports.computeIfAbsent(player.getUuid(), uuid -> new MinestomPlayerTransport(player, CHANNEL));
        Session session = new Session(SessionRole.SERVER, transports.get(player.getUuid()));
        session.setLogSink(msg -> System.out.println("[moud][" + player.getUsername() + "] " + msg));
        session.setMessageHandler((lane, message) -> onSessionMessage(player, lane, message));
        session.start();
        sessions.put(player.getUuid(), session);
        activeSceneId.putIfAbsent(player.getUuid(), "main");
        playRuntime.onPlayerSpawn(player, activeSceneId.getOrDefault(player.getUuid(), "main"));
    }

    private void onPluginMessage(PlayerPluginMessageEvent event) {
        MinestomPlayerTransport transport = transports.get(event.getPlayer().getUuid());
        if (transport == null) {
            return;
        }
        transport.acceptPluginMessage(event.getIdentifier(), event.getMessage());
        Session session = sessions.get(event.getPlayer().getUuid());
        if (session != null) {
            session.tick();
        }
    }

    private void onDisconnect(Player player) {
        sessions.remove(player.getUuid());
        transports.remove(player.getUuid());
        schemaSent.remove(player.getUuid());
        scenesSent.remove(player.getUuid());
        scenesSentRevision.remove(player.getUuid());
        activeSceneId.remove(player.getUuid());
        playRuntime.onDisconnect(player.getUuid());
    }

    private void tick() {
        scenes.tickAll(TICK_DT_SECONDS);
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            Session session = entry.getValue();
            if (session.state() == SessionState.CONNECTED && schemaSent.putIfAbsent(uuid, Boolean.TRUE) == null) {
                SchemaSnapshot schema = buildSchemaSnapshot();
                session.send(Lane.STATE, schema);
            }
            if (session.state() == SessionState.CONNECTED) {
                sendSceneListIfNeeded(uuid, session);
                String sceneId = activeSceneId.getOrDefault(uuid, "main");
                ServerScene scene = scenes.get(sceneId);
                if (scene == null) {
                    scene = mainScene;
                }
                if (scene != null) {
                    playRuntime.tick(uuid, session, scene);
                }
            }
        }
        for (Session session : sessions.values()) {
            session.tick();
        }
    }

    private void sendSceneListIfNeeded(UUID uuid, Session session) {
        long rev = scenes.scenesRevision();
        long last = scenesSentRevision.getOrDefault(uuid, Long.MIN_VALUE);
        if (scenesSent.putIfAbsent(uuid, Boolean.TRUE) != null && last == rev) {
            return;
        }
        scenesSentRevision.put(uuid, rev);
        String active = activeSceneId.getOrDefault(uuid, "main");
        session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), active));
    }

    private SchemaSnapshot buildSchemaSnapshot() {
        ArrayList<NodeTypeDef> types = new ArrayList<>(mainScene.engine().nodeTypes().types().values());
        types.sort(Comparator
                .comparingInt(NodeTypeDef::order)
                .thenComparing(NodeTypeDef::uiLabel)
                .thenComparing(NodeTypeDef::typeId));
        return new SchemaSnapshot(1L, java.util.List.copyOf(types));
    }

    private void registerCommands() {
        Command command = new Command("moud");

        ArgumentWord sub = new ArgumentWord("sub");
        ArgumentWord path = new ArgumentWord("path");
        ArgumentWord name = new ArgumentWord("name");
        ArgumentWord sceneId = new ArgumentWord("sceneId");
        ArgumentWord res = new ArgumentWord("res");

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if ("dump".equalsIgnoreCase(s)) {
                sender.sendMessage(mainScene.engine().dumpScene());
                return;
            }
            if ("stats".equalsIgnoreCase(s)) {
                sender.sendMessage("ticks=" + mainScene.engine().ticks() + " lastDumpTick=" + mainScene.engine().lastDumpTick());
                return;
            }
            sender.sendMessage("Usage: /moud dump | /moud stats");
        }, sub);

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if (!"saveScene".equalsIgnoreCase(s)) {
                sender.sendMessage("Usage: /moud saveScene <sceneId> <res://path>");
                return;
            }
            if (assets == null) {
                sender.sendMessage("Assets disabled.");
                return;
            }
            String sid = context.get(sceneId);
            String resRaw = context.get(res);
            ResPath resPath;
            try {
                resPath = normalizeResPath(resRaw);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid res path: " + e.getMessage());
                return;
            }

            com.moud.server.minestom.engine.ServerScene scene = scenes.get(sid);
            if (scene == null) {
                scene = mainScene;
            }

            SceneSnapshot snapshot = scene.snapshot(0L);
            byte[] bytes = WireMessages.encode(snapshot);
            AssetHash hash = AssetHash.sha256(bytes);
            AssetMeta meta = new AssetMeta(hash, bytes.length, AssetType.BINARY);
            try {
                assets.store().put(resPath, meta, bytes);
                sender.sendMessage("Saved scene '" + scene.sceneId() + "' to " + resPath.value() + " (" + bytes.length + " bytes)");
            } catch (Exception e) {
                sender.sendMessage("Save failed: " + e.getMessage());
            }
        }, sub, sceneId, res);

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if (!"loadScene".equalsIgnoreCase(s)) {
                sender.sendMessage("Usage: /moud loadScene <sceneId> <res://path>");
                return;
            }
            if (assets == null) {
                sender.sendMessage("Assets disabled.");
                return;
            }
            String sid = context.get(sceneId);
            String resRaw = context.get(res);
            ResPath resPath;
            try {
                resPath = normalizeResPath(resRaw);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid res path: " + e.getMessage());
                return;
            }

            com.moud.server.minestom.engine.ServerScene scene = scenes.get(sid);
            if (scene == null) {
                scene = mainScene;
            }

            AssetMeta meta = assets.store().meta(resPath);
            if (meta == null) {
                sender.sendMessage("Not found: " + resPath.value());
                return;
            }
            byte[] bytes;
            try {
                bytes = assets.store().readBlob(meta.hash());
            } catch (Exception e) {
                sender.sendMessage("Read failed: " + e.getMessage());
                return;
            }

            Message decoded;
            try {
                decoded = WireMessages.decode(bytes);
            } catch (Exception e) {
                sender.sendMessage("Decode failed: " + e.getMessage());
                return;
            }
            if (!(decoded instanceof SceneSnapshot snapshot)) {
                sender.sendMessage("Asset is not a SceneSnapshot: " + decoded.type());
                return;
            }

            var specs = toNodeSpecs(snapshot);
            SceneTreeMutator.replaceRootChildren(scene.engine().sceneTree(), specs, scene.engine().nodeTypes());
            scene.engine().bumpSceneRevision();
            scene.engine().bumpCsgRevision();
            sender.sendMessage("Loaded scene '" + scene.sceneId() + "' from " + resPath.value());
        }, sub, sceneId, res);

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if (!"find".equalsIgnoreCase(s)) {
                sender.sendMessage("Usage: /moud find <path>");
                return;
            }
            String p = context.get(path);
            Node node = mainScene.engine().sceneTree().getNode(p);
            if (node == null) {
                sender.sendMessage("Not found: " + p);
                return;
            }
            sender.sendMessage("Found: " + node.path() + " children=" + node.children().size());
        }, sub, path);

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if (!"add".equalsIgnoreCase(s)) {
                sender.sendMessage("Usage: /moud add <parentPath> <name>");
                return;
            }
            String parentPath = context.get(path);
            String childName = context.get(name);
            Node parent = mainScene.engine().sceneTree().getNode(parentPath);
            if (parent == null) {
                sender.sendMessage("Parent not found: " + parentPath);
                return;
            }
            parent.addChild(new PlainNode(childName));
            mainScene.engine().bumpSceneRevision();
            sender.sendMessage("Added: " + parent.path() + "/" + childName);
        }, sub, path, name);

        command.addSyntax((sender, context) -> {
            String s = context.get(sub);
            if (!"free".equalsIgnoreCase(s)) {
                sender.sendMessage("Usage: /moud free <path>");
                return;
            }
            String p = context.get(path);
            Node node = mainScene.engine().sceneTree().getNode(p);
            if (node == null) {
                sender.sendMessage("Not found: " + p);
                return;
            }
            if (node.parent() == null) {
                sender.sendMessage("Refusing to free root node.");
                return;
            }
            node.queueFree();
            mainScene.engine().bumpSceneRevision();
            sender.sendMessage("Queued free: " + node.path());
        }, sub, path);

        MinecraftServer.getCommandManager().register(command);
    }

    private static ResPath normalizeResPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("res://")) {
            trimmed = "res://" + trimmed;
        }
        return new ResPath(trimmed);
    }

    private static java.util.List<SceneTreeMutator.NodeSpec> toNodeSpecs(SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return java.util.List.of();
        }

        long rootId = 0L;
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node != null && node.parentId() == 0L) {
                rootId = node.nodeId();
                break;
            }
        }

        ArrayList<SceneTreeMutator.NodeSpec> out = new ArrayList<>(snapshot.nodes().size());
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node == null) {
                continue;
            }
            if (node.nodeId() == rootId) {
                continue;
            }
            LinkedHashMap<String, String> props = new LinkedHashMap<>();
            if (node.properties() != null) {
                for (SceneSnapshot.Property p : node.properties()) {
                    if (p == null || p.key() == null || p.key().isBlank() || p.value() == null) {
                        continue;
                    }
                    props.put(p.key(), p.value());
                }
            }
            out.add(new SceneTreeMutator.NodeSpec(node.nodeId(), node.parentId(), node.name(), node.type(), props));
        }
        return java.util.List.copyOf(out);
    }

    private void onSessionMessage(Player player, Lane lane, Message message) {
        Session session = sessions.get(player.getUuid());
        if (session == null) {
            return;
        }

        if (lane == Lane.INPUT && message instanceof PlayerInput input) {
            playRuntime.onInput(player.getUuid(), input);
            return;
        }

        if (lane == Lane.STATE && message instanceof SceneSelect(String sceneId)) {
            com.moud.server.minestom.engine.ServerScene next = scenes.get(sceneId);
            if (next == null) {
                next = scenes.get("main");
            }
            if (next == null) {
                next = mainScene;
            }
            if (next != null) {
                activeSceneId.put(player.getUuid(), next.sceneId());
                playRuntime.onSceneChanged(player.getUuid(), next.sceneId());
                player.setInstance(next.instance(), new Pos(0, 42, 0));
                session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), next.sceneId()));
                session.send(Lane.STATE, next.snapshot(0L));
            }
            return;
        }

        String sceneId = activeSceneId.getOrDefault(player.getUuid(), "main");
        com.moud.server.minestom.engine.ServerScene scene = scenes.get(sceneId);
        if (scene == null) {
            scene = mainScene;
        }

        if (message instanceof SceneSnapshotRequest(long requestId)) {
            SceneSnapshot snapshot = scene.snapshot(requestId);
            session.send(Lane.STATE, snapshot);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneOpBatch batch) {
            if (!devMode) {
                ArrayList<SceneOpResult> results = new ArrayList<>(batch.ops().size());
                for (SceneOp op : batch.ops()) {
                    long target = switch (op) {
                        case SceneOp.CreateNode create -> create.parentId();
                        case SceneOp.QueueFree qf -> qf.nodeId();
                        case SceneOp.Rename rn -> rn.nodeId();
                        case SceneOp.SetProperty sp -> sp.nodeId();
                        case SceneOp.RemoveProperty rp -> rp.nodeId();
                        case SceneOp.Reparent rp -> rp.nodeId();
                    };
                    results.add(SceneOpResult.fail(target, SceneOpError.INVALID, "editor disabled (MOUD_MODE=player)"));
                }
                session.send(Lane.EVENTS, new SceneOpAck(batch.batchId(), scene.engine().sceneRevision(), java.util.List.copyOf(results)));
                return;
            }
            String user = player.getUsername();
            String sid = scene.sceneId();
            scene.applier().setLogSink(s -> System.out.println("[moud][scene][" + user + "][" + sid + "] " + s));
            SceneOpAck ack = scene.apply(batch);
            session.send(Lane.EVENTS, ack);
            return;
        }

        if (lane == Lane.ASSETS && assets != null) {
            assets.onMessage(player.getUuid(), session, message);
        }
    }
}
