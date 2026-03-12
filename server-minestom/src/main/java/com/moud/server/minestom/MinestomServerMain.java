package com.moud.server.minestom;

import com.moud.core.NodeTypeDef;
import com.moud.core.ProtocolVersions;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Node;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfoRequest;
import com.moud.net.protocol.RequestRespawn;
import com.moud.net.protocol.SceneCreate;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDelete;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.SceneSave;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSelect;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.net.protocol.ServerHello;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.net.wire.WireMessages;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.server.minestom.assets.AssetService;
import com.moud.server.minestom.assets.FileSystemAssetStore;
import com.moud.server.minestom.engine.SceneInstancer;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.runtime.PlayRuntime;
import com.moud.server.minestom.scene.SceneFileIO;
import com.moud.server.minestom.scripting.ScriptService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import java.util.Locale;
import com.moud.server.minestom.util.DebugLog;
import com.moud.core.scene.PlainNode;

public final class MinestomServerMain {
    private static final String CHANNEL = "moud:engine";
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

    private boolean devMode = true;
    private ServerScenes scenes;
    private ServerScene mainScene;
    private AssetService assets;
    private ProjectService project;
    private ScriptService scripts;
    private final PlayRuntime playRuntime = new PlayRuntime();
    private final SceneInstancer instancer = new SceneInstancer();
    private Path projectRoot = Path.of(".");

    public static void main(String[] args) {
        new MinestomServerMain().run();
    }

    private void run() {
        // Suppress GraalVM interpreter-only warning when running without JVMCI
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");

        String mode = System.getenv().getOrDefault("MOUD_MODE", "dev").trim();
        devMode = !"player".equalsIgnoreCase(mode);
        DebugLog.info("moud", "mode=" + (devMode ? "dev" : "player"));

        String rootEnv = System.getenv().getOrDefault("MOUD_PROJECT_ROOT", ".").trim();
        if (!rootEnv.isEmpty()) {
            try {
                projectRoot = Path.of(rootEnv).toAbsolutePath().normalize();
            } catch (Exception ignored) {
                projectRoot = Path.of(".").toAbsolutePath().normalize();
            }
        } else {
            projectRoot = Path.of(".").toAbsolutePath().normalize();
        }
        DebugLog.info("moud", "projectRoot=" + projectRoot);

        MinecraftServer minecraftServer = MinecraftServer.init();
        MinecraftServer.getConnectionManager().setPlayerProvider(EnginePlayer::new);

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        scenes = new ServerScenes(instanceManager);
        mainScene = scenes.ensureDefault("main", "Main");
        project = new ProjectService(projectRoot);
        scripts = new ScriptService(project);

        try {
            assets = new AssetService(new FileSystemAssetStore(projectRoot.resolve("assets")), devMode);
            assets.setUploadCompleteCallback(this::onAssetUploaded);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init asset store", e);
        }

        loadScenesFromDisk();
        instancer.syncAll(scenes);

        MinecraftServer.getGlobalEventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(mainScene.instance());
                    // Spawn directly at PlayerStart if one exists, avoiding a visible
                    // "wrong position first frame" flash.
                    Pos startPos = PlayRuntime.findPlayerStartPos(mainScene);
                    event.getPlayer().setRespawnPoint(startPos != null ? startPos : new Pos(0, 64, 0));
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
                .buildTask(() -> DebugLog.info("server", "listening on :25565"))
                .delay(Duration.ofMillis(100))
                .schedule();

        minecraftServer.start("0.0.0.0", 25565);
    }

    private void onPlayerSpawn(Player player) {
        if (!(player instanceof EnginePlayer enginePlayer)) {
            return;
        }

        enginePlayer.setRespawnPoint(new Pos(0, 64, 0));
        enginePlayer.setGameMode(GameMode.CREATIVE);
        enginePlayer.sendPluginMessage("minecraft:register", CHANNEL.getBytes(StandardCharsets.UTF_8));

        if (enginePlayer.transport() == null) {
            enginePlayer.setTransport(new MinestomPlayerTransport(enginePlayer, CHANNEL));
        }
        if (enginePlayer.session() == null) {
            Session session = new Session(SessionRole.SERVER, enginePlayer.transport());
            session.setServerHelloSupplier(() -> new ServerHello(ProtocolVersions.PROTOCOL_VERSION, devMode));
            session.setLogSink(msg -> DebugLog.debug("session/" + enginePlayer.getUsername(), msg));
            session.setMessageHandler((lane, message) -> onSessionMessage(enginePlayer, lane, message));
            session.start();
            enginePlayer.setSession(session);
        }

        ServerScene spawnScene = scenes.get(enginePlayer.activeSceneId());
        if (spawnScene == null) spawnScene = mainScene;
        playRuntime.onPlayerSpawn(enginePlayer, spawnScene);
    }

    private void onPluginMessage(PlayerPluginMessageEvent event) {
        if (!(event.getPlayer() instanceof EnginePlayer enginePlayer)) {
            return;
        }

        MinestomPlayerTransport transport = enginePlayer.transport();
        if (transport == null) {
            return;
        }
        transport.acceptPluginMessage(event.getIdentifier(), event.getMessage());
        Session session = enginePlayer.session();
        if (session != null) {
            session.tick();
        }
    }

    private void onDisconnect(Player player) {
        playRuntime.onDisconnect(player.getUuid());
    }

    private void tick() {
        scenes.tickAll(TICK_DT_SECONDS);

        Map<UUID, float[]> playerPositions = new java.util.HashMap<>();
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            Pos pos = p.getPosition();
            playerPositions.put(p.getUuid(), new float[]{(float) pos.x(), (float) pos.y(), (float) pos.z(), pos.yaw()});
        }
        scripts.updatePlayerPositions(playerPositions);

        for (ServerScene scene : scenes.allScenes()) {
            playRuntime.applyEditorWorldEnvironment(scene);
            String pendingTransition = scripts.tickRuntime(scene, TICK_DT_SECONDS);
            if (pendingTransition != null) {
                applyScriptSceneTransition(scene.sceneId(), pendingTransition);
            }
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!(player instanceof EnginePlayer enginePlayer)) {
                continue;
            }
            Session session = enginePlayer.session();
            if (session == null) {
                continue;
            }

            if (session.state() == SessionState.CONNECTED && !enginePlayer.schemaSent()) {
                session.send(Lane.STATE, buildSchemaSnapshot());
                enginePlayer.markSchemaSent();
            }

            if (session.state() == SessionState.CONNECTED) {
                sendSceneListIfNeeded(enginePlayer, session);
                String sceneId = enginePlayer.activeSceneId();
                ServerScene scene = scenes.get(sceneId);
                if (scene == null) {
                    scene = mainScene;
                }
                if (scene != null) {
                    float[] followCam = scripts.getFollowCameraForPlayer(scene.sceneId(), enginePlayer.getUuid());
                    Long playerCamId = followCam == null
                            ? scripts.getActiveCameraForPlayer(scene.sceneId(), enginePlayer.getUuid())
                            : null;
                    playRuntime.tick(enginePlayer.getUuid(), session, scene, playerCamId, followCam);
                }
            }

            session.tick();
        }
    }

    private void sendSceneListIfNeeded(EnginePlayer player, Session session) {
        long rev = scenes.scenesRevision();
        long last = player.scenesSentRevision();
        if (last == rev) {
            return;
        }
        player.setScenesSentRevision(rev);
        String active = player.activeSceneId();
        if (active == null || active.isBlank() || scenes.get(active) == null) {
            active = "main";
            player.setActiveSceneId(active);
        }
        session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), active));
    }

    private SchemaSnapshot buildSchemaSnapshot() {
        ArrayList<NodeTypeDef> types = new ArrayList<>(mainScene.engine().nodeTypes().types().values());
        types.sort(Comparator
                .comparingInt(NodeTypeDef::order)
                .thenComparing(NodeTypeDef::uiLabel)
                .thenComparing(NodeTypeDef::typeId));
        return new SchemaSnapshot(1L, List.copyOf(types));
    }

    private void loadScenesFromDisk() {
        Path scenesDir = projectRoot.resolve("scenes");
        if (!Files.exists(scenesDir) || !Files.isDirectory(scenesDir)) {
            return;
        }

        try (var stream = Files.list(scenesDir)) {
            stream.filter(p -> p != null && p.getFileName() != null && p.getFileName().toString().endsWith(".moud.scene"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
                        if (sceneId.isBlank()) {
                            return;
                        }

                        try {
                            String json = Files.readString(p, StandardCharsets.UTF_8);
                            var file = SceneFileIO.parse(json);
                            String displayName = file.displayName() == null || file.displayName().isBlank()
                                    ? sceneId
                                    : file.displayName();

                            ServerScene scene = scenes.ensureDefault(sceneId, displayName);
                            var specs = SceneFileIO.toNodeSpecs(file);
                            SceneTreeMutator.replaceRootChildren(scene.engine().sceneTree(), specs, scene.engine().nodeTypes());
                            scene.engine().bumpSceneRevision();
                            scene.engine().bumpCsgRevision();
                            DebugLog.info("scene", "loaded '" + sceneId + "' from " + p);
                        } catch (Exception e) {
                            DebugLog.error("scene", "failed to load '" + sceneId + "': " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            DebugLog.error("scene", "failed to scan scenes/: " + e.getMessage());
        }
    }

    private void onAssetUploaded(ResPath path, AssetMeta meta, byte[] bytes) {
        if (path == null || !path.path().endsWith(".moud.scene")) {
            return;
        }

        String filename = path.path().substring(path.path().lastIndexOf('/') + 1);
        String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
        if (sceneId.isBlank()) {
            DebugLog.warn("scene", "invalid scene filename: " + filename);
            return;
        }

        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            var file = SceneFileIO.parse(json);
            String displayName = file.displayName() == null || file.displayName().isBlank()
                    ? sceneId
                    : file.displayName();

            ServerScene scene = scenes.ensureDefault(sceneId, displayName);
            var specs = SceneFileIO.toNodeSpecs(file);
            SceneTreeMutator.replaceRootChildren(scene.engine().sceneTree(), specs, scene.engine().nodeTypes());
            scene.engine().bumpSceneRevision();
            scene.engine().bumpCsgRevision();
            DebugLog.info("scene", "imported '" + sceneId + "' from " + path.path());
            try {
                persistSceneToDisk(scene);
            } catch (Exception e) {
                DebugLog.error("scene", "failed to persist imported scene '" + sceneId + "': " + e.getMessage());
            }
            instancer.syncAll(scenes);
        } catch (Exception e) {
            DebugLog.error("scene", "failed to import '" + sceneId + "': " + e.getMessage());
        }
    }

    private void persistSceneToDisk(ServerScene scene) throws Exception {
        if (scene == null) {
            throw new IllegalArgumentException("scene null");
        }
        Path out = sceneFilePath(scene.sceneId());
        Files.createDirectories(out.getParent());
        String json = SceneFileIO.toJson(scene.sceneId(), scene.displayName(), scene.snapshot(0L));
        Path tmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
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

            ServerScene scene = scenes.get(sid);
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

            ServerScene scene = scenes.get(sid);
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

    private static List<SceneTreeMutator.NodeSpec> toNodeSpecs(SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return List.of();
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
        return List.copyOf(out);
    }

    private void onSessionMessage(Player player, Lane lane, Message message) {
        if (!(player instanceof EnginePlayer enginePlayer)) {
            return;
        }

        Session session = enginePlayer.session();
        if (session == null) {
            return;
        }

        if (lane == Lane.STATE && message instanceof ProjectInfoRequest request) {
            session.send(Lane.STATE, project.info(request.requestId()));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ProjectCreate create) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ProjectCreateAck(create.requestId(), false, "editor disabled (MOUD_MODE=player)", "", ""));
                return;
            }
            session.send(Lane.EVENTS, project.create(create));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptActionListRequest request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptActionListResponse(request.requestId(), request.nodeId(), false, "editor disabled (MOUD_MODE=player)", List.of()));
                return;
            }
            session.send(Lane.EVENTS, scripts.onListActions(resolvePlayerScene(enginePlayer), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptActionInvoke request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptActionInvokeAck(request.requestId(), request.nodeId(), false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            session.send(Lane.EVENTS, scripts.onInvokeAction(resolvePlayerScene(enginePlayer), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileReadRequest request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), false, request.path(), "", "editor disabled (MOUD_MODE=player)"));
                return;
            }
            String raw = request.path();
            String path = raw == null ? "" : raw.trim();
            if (path.isEmpty()) {
                DebugLog.warn("script-files", "read rejected: empty path user=" + player.getUsername());
                session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), false, raw, "", "Script path is required"));
                return;
            }
            String allowPath;
            try {
                allowPath = normalizeAllowedScriptPath(path);
            } catch (Exception e) {
                DebugLog.warn("script-files", "read rejected path='" + path + "' user=" + player.getUsername() + ": " + e.getMessage());
                session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), false, raw, "", e.getMessage()));
                return;
            }
            try {
                Path file = project.resolveProjectPath(allowPath);
                if (!Files.isRegularFile(file)) {
                    DebugLog.warn("script-files", "read not found path='" + allowPath + "' user=" + player.getUsername());
                    session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), false, allowPath, "", "Script not found: " + allowPath));
                    return;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), true, allowPath, content == null ? "" : content, null));
            } catch (Exception e) {
                DebugLog.error("script-files", "read failed path='" + allowPath + "' user=" + player.getUsername() + ": " + e.getMessage(), e);
                String msg = e.getMessage() == null ? "Read failed" : e.getMessage();
                session.send(Lane.EVENTS, new ScriptFileReadResponse(request.requestId(), false, allowPath, "", msg));
            }
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileWriteRequest request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptFileWriteAck(request.requestId(), false, request.path(), "editor disabled (MOUD_MODE=player)"));
                return;
            }
            String raw = request.path();
            String path = raw == null ? "" : raw.trim();
            if (path.isEmpty()) {
                DebugLog.warn("script-files", "write rejected: empty path user=" + player.getUsername());
                session.send(Lane.EVENTS, new ScriptFileWriteAck(request.requestId(), false, raw, "Script path is required"));
                return;
            }
            String allowPath;
            try {
                allowPath = normalizeAllowedScriptPath(path);
            } catch (Exception e) {
                DebugLog.warn("script-files", "write rejected path='" + path + "' user=" + player.getUsername() + ": " + e.getMessage());
                session.send(Lane.EVENTS, new ScriptFileWriteAck(request.requestId(), false, raw, e.getMessage()));
                return;
            }
            String content = request.content() == null ? "" : request.content();
            try {
                Path file = project.resolveProjectPath(allowPath);
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                DebugLog.debug("script-files", "write ok path='" + allowPath + "' bytes=" + content.length() + " user=" + player.getUsername());
                session.send(Lane.EVENTS, new ScriptFileWriteAck(request.requestId(), true, allowPath, null));
            } catch (Exception e) {
                DebugLog.error("script-files", "write failed path='" + allowPath + "' user=" + player.getUsername() + ": " + e.getMessage(), e);
                String msg = e.getMessage() == null ? "Write failed" : e.getMessage();
                session.send(Lane.EVENTS, new ScriptFileWriteAck(request.requestId(), false, allowPath, msg));
            }
            return;
        }

        if (lane == Lane.INPUT && message instanceof PlayerInput input) {
            scripts.onPlayerInput(enginePlayer.getUuid(), input);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof RequestRespawn) {
            String sceneId = enginePlayer.activeSceneId();
            ServerScene scene = sceneId != null ? scenes.get(sceneId) : null;
            if (scene == null) scene = mainScene;
            Pos startPos = PlayRuntime.findPlayerStartPos(scene);
            if (startPos != null) {
                player.teleport(startPos);
            }
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneSave save) {
            String sid = save.sceneId();
            if (sid == null || sid.isBlank()) {
                sid = enginePlayer.activeSceneId();
            }
            if (sid == null || sid.isBlank()) {
                sid = "main";
            }
            if (!devMode) {
                session.send(Lane.EVENTS, new SceneSaveAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }

            ServerScene scene = scenes.get(sid);
            if (scene == null) {
                session.send(Lane.EVENTS, new SceneSaveAck(sid, false, "Scene not found"));
                return;
            }

            try {
                persistSceneToDisk(scene);
                session.send(Lane.EVENTS, new SceneSaveAck(scene.sceneId(), true, null));
            } catch (Exception e) {
                String msg = e.getMessage() == null || e.getMessage().isBlank() ? "Save failed" : e.getMessage();
                session.send(Lane.EVENTS, new SceneSaveAck(scene.sceneId(), false, msg));
            }
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneCreate create) {
            String sid = normalizeSceneId(create.sceneId());
            String displayName = create.displayName();
            if (displayName != null) {
                displayName = displayName.trim();
            }

            if (!devMode) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            if (!isValidSceneId(sid)) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "Invalid scene id (use [a-z0-9_-], max 64 chars)"));
                return;
            }
            if (scenes.get(sid) != null) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "Scene already exists"));
                return;
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = sid;
            }

            ServerScene scene;
            try {
                scene = scenes.create(sid, displayName);
                persistSceneToDisk(scene);
            } catch (Exception e) {
                scenes.delete(sid);
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, e.getMessage()));
                return;
            }

            session.send(Lane.EVENTS, new SceneCreateAck(sid, true, null));
            switchPlayerToScene(enginePlayer, session, scene);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneDelete delete) {
            String sid = normalizeSceneId(delete.sceneId());
            if (!devMode) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            if (sid == null || sid.isBlank()) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Scene id required"));
                return;
            }
            if ("main".equals(sid)) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Refusing to delete 'main'"));
                return;
            }

            ServerScene existing = scenes.get(sid);
            if (existing == null) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Scene not found"));
                return;
            }

            try {
                Files.deleteIfExists(sceneFilePath(sid));
            } catch (Exception e) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Failed to delete scene file: " + e.getMessage()));
                return;
            }

            scenes.delete(sid);
            scripts.onSceneDeleted(sid);

            for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (!(p instanceof EnginePlayer ep)) {
                    continue;
                }
                if (!sid.equals(ep.activeSceneId())) {
                    continue;
                }
                Session s = ep.session();
                if (s == null) {
                    ep.setActiveSceneId("main");
                    playRuntime.onSceneChanged(ep.getUuid(), "main");
                    continue;
                }
                switchPlayerToScene(ep, s, mainScene);
            }

            session.send(Lane.EVENTS, new SceneDeleteAck(sid, true, null));
            return;
        }

        if (lane == Lane.STATE && message instanceof SceneSelect(String sceneId)) {
            ServerScene next = scenes.get(sceneId);
            if (next == null && !"main".equals(sceneId)) {
                DebugLog.warn("scene", "unknown scene '" + sceneId + "', switching to main");
                next = scenes.get("main");
            }
            if (next == null) {
                next = mainScene;
            }
            if (next == null) {
                return;
            }

            if (next.sceneId().equals(enginePlayer.activeSceneId())) {
                return;
            }

            switchPlayerToScene(enginePlayer, session, next);
            return;
        }

        String sceneId = enginePlayer.activeSceneId();
        if (sceneId == null || sceneId.isBlank()) {
            sceneId = "main";
        }
        ServerScene scene = scenes.get(sceneId);
        if (scene == null) {
            scene = mainScene;
        }

        if (message instanceof SceneSnapshotRequest(long requestId)) {
            instancer.syncScene(scenes, scene);
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
                session.send(Lane.EVENTS, new SceneOpAck(batch.batchId(), scene.engine().sceneRevision(), List.copyOf(results)));
                return;
            }
            String user = player.getUsername();
            String sid = scene.sceneId();
            scene.applier().setLogSink(s -> DebugLog.debug("scene/" + sid, "[" + user + "] " + s));
            SceneOpAck ack = scene.apply(batch);
            instancer.syncScene(scenes, scene);
            if (ack != null && ack.sceneRevision() != scene.engine().sceneRevision()) {
                ack = new SceneOpAck(ack.batchId(), scene.engine().sceneRevision(), ack.results());
            }
            if (ack != null && ack.results() != null) {
                for (SceneOpResult r : ack.results()) {
                    if (r == null || r.ok()) {
                        continue;
                    }
                    String msg = r.message();
                    if (msg == null || msg.isBlank()) {
                        msg = r.error() == null ? "SceneOp failed" : r.error().name();
                    }
                    DebugLog.error("scene", "apply failed user=" + user + " scene=" + sid + " targetId=" + r.targetId() + " error=" + msg);
                }
            }
            session.send(Lane.EVENTS, ack);
            return;
        }

        if (lane == Lane.ASSETS && assets != null) {
            assets.onMessage(enginePlayer.getUuid(), session, message);
        }
    }

    private ServerScene resolvePlayerScene(EnginePlayer player) {
        if (player == null) {
            return mainScene;
        }
        String sceneId = player.activeSceneId();
        if (sceneId == null || sceneId.isBlank()) {
            sceneId = "main";
        }
        ServerScene scene = scenes == null ? null : scenes.get(sceneId);
        if (scene == null) {
            scene = mainScene;
        }
        return scene;
    }

    private void applyScriptSceneTransition(String fromSceneId, String toSceneId) {
        if (fromSceneId == null || toSceneId == null || toSceneId.isBlank()) {
            return;
        }
        ServerScene target = scenes.get(toSceneId);
        if (target == null) {
            DebugLog.warn("scene", "script requested unknown scene '" + toSceneId + "'");
            return;
        }
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!(p instanceof EnginePlayer ep)) {
                continue;
            }
            if (!fromSceneId.equals(ep.activeSceneId())) {
                continue;
            }
            Session s = ep.session();
            if (s != null) {
                switchPlayerToScene(ep, s, target);
            }
        }
    }

    private void switchPlayerToScene(EnginePlayer player, Session session, ServerScene target) {
        if (player == null || session == null || target == null) {
            return;
        }
        String targetId = target.sceneId();
        player.setActiveSceneId(targetId);
        playRuntime.onSceneChanged(player.getUuid(), targetId);

        Pos targetStartPos = PlayRuntime.findPlayerStartPos(target);
        Pos spawnPos = targetStartPos != null ? targetStartPos : new Pos(0, 64, 0);
        player.setInstance(target.instance(), spawnPos)
                .thenRun(() -> MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (session.state() != SessionState.CONNECTED) {
                        return;
                    }
                    session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), targetId));
                    instancer.syncScene(scenes, target);
                    session.send(Lane.STATE, target.snapshot(0L));
                }).schedule())
                .exceptionally(ex -> {
                    DebugLog.error("scene", "failed to switch to '" + targetId + "': " + ex.getMessage());
                    return null;
                });
    }

    private Path sceneFilePath(String sceneId) {
        return projectRoot.resolve("scenes").resolve(sceneId + ".moud.scene");
    }

    private static String normalizeSceneId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAllowedScriptPath(String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Script path is required");
        }
        if (path.startsWith(ResPath.SCHEME)) {
            ResPath rp = new ResPath(path);
            String inner = rp.path();
            if (!inner.startsWith("scripts/")) {
                throw new IllegalArgumentException("Only res://scripts/ paths are allowed");
            }
            return rp.value();
        }
        if (!path.startsWith("scripts/")) {
            throw new IllegalArgumentException("Only scripts/ paths are allowed");
        }
        return path;
    }

    private static boolean isValidSceneId(String sceneId) {
        if (sceneId == null) {
            return false;
        }
        String id = sceneId.trim();
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            return false;
        }
        return true;
    }
}
