package com.moud.server.minestom;

import com.moud.core.NodeTypeDef;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.net.protocol.*;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.net.MinestomPlayerTransport;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomServerMain {
    private static final String CHANNEL = "moud:engine";
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

    private final Map<UUID, MinestomPlayerTransport> transports = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> schemaSent = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> scenesSent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scenesSentRevision = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeSceneId = new ConcurrentHashMap<>();
    private com.moud.server.minestom.engine.ServerScenes scenes;
    private com.moud.server.minestom.engine.ServerScene mainScene;

    public static void main(String[] args) {
        new MinestomServerMain().run();
    }

    private void run() {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        scenes = new com.moud.server.minestom.engine.ServerScenes(instanceManager);
        mainScene = scenes.ensureDefault("main", "Main");
        scenes.create("sandbox", "Sandbox");

        // Default CSG block so it's obvious the pipeline works without clicking anything.
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
                    // Required by Minestom: the spawning instance must be provided during configuration.
                    event.setSpawningInstance(mainScene.instance());
                    event.getPlayer().setRespawnPoint(new Pos(0, 42, 0));
                })
                .addListener(PlayerSpawnEvent.class, event -> onPlayerSpawn(event.getPlayer()))
                .addListener(PlayerPluginMessageEvent.class, this::onPluginMessage)
                .addListener(PlayerDisconnectEvent.class, event -> onDisconnect(event.getPlayer()));

        registerCommands();

        // Tick sessions + engine at 20Hz (server tick); keep deterministic.
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
        // Advertise our payload channel to Fabric clients (required for ClientPlayNetworking.canSend()).
        player.sendPluginMessage("minecraft:register", CHANNEL.getBytes(StandardCharsets.UTF_8));
        transports.computeIfAbsent(player.getUuid(), uuid -> new MinestomPlayerTransport(player, CHANNEL));
        Session session = new Session(SessionRole.SERVER, transports.get(player.getUuid()));
        session.setLogSink(msg -> System.out.println("[moud][" + player.getUsername() + "] " + msg));
        session.setMessageHandler((lane, message) -> onSessionMessage(player, lane, message));
        session.start();
        sessions.put(player.getUuid(), session);
        activeSceneId.putIfAbsent(player.getUuid(), "main");
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
    }

    private void tick() {
        scenes.tickAll(TICK_DT_SECONDS);
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            Session session = entry.getValue();
            if (session.state() == com.moud.net.session.SessionState.CONNECTED && schemaSent.putIfAbsent(uuid, Boolean.TRUE) == null) {
                SchemaSnapshot schema = buildSchemaSnapshot();
                session.send(Lane.STATE, schema);
            }
            if (session.state() == com.moud.net.session.SessionState.CONNECTED) {
                sendSceneListIfNeeded(uuid, session);
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

    private void onSessionMessage(Player player, Lane lane, Message message) {
        Session session = sessions.get(player.getUuid());
        if (session == null) {
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
            String user = player.getUsername();
            String sid = scene.sceneId();
            scene.applier().setLogSink(s -> System.out.println("[moud][scene][" + user + "][" + sid + "] " + s));
            SceneOpAck ack = scene.apply(batch);
            session.send(Lane.EVENTS, ack);
        }
    }
}
