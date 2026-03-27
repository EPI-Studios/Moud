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
import com.moud.server.minestom.scripting.ScriptFileService;
import com.moud.server.minestom.scripting.ScriptService;
import com.moud.server.minestom.util.DebugLog;
import com.moud.core.scene.PlainNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceManager;

/**
 * Embeddable Moud server library class.
 *
 * <p>Usage:
 * <pre>
 *   MinecraftServer server = MinecraftServer.init();
 *   MoudServer moud = MoudServer.fromEnvironment().build();
 *   moud.register(MinecraftServer.getGlobalEventHandler(), MinecraftServer.getInstanceManager());
 *   moud.registerTickTask();
 *   server.start("0.0.0.0", 25565);
 * </pre>
 *
 * <p>No {@code PlayerProvider} is set — Moud works with any existing Player subclass.
 */
public final class MoudServer {

    private static final String CHANNEL = "moud:engine";
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

    // -------------------------------------------------------------------------
    // Per-player state (replaces the old EnginePlayer subclass)
    // -------------------------------------------------------------------------

    private static final class PlayerState {
        MinestomPlayerTransport transport;
        Session session;
        String activeSceneId = "main";
        boolean schemaSent;
        long scenesSentRevision = Long.MIN_VALUE;
        boolean editorMode;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final boolean devMode;
    private final Path projectRoot;

    private ServerScenes scenes;
    private ServerScene mainScene;
    private AssetService assets;
    private ProjectService project;
    private ScriptService scripts;
    private ScriptFileService scriptFiles;
    private final PlayRuntime playRuntime = new PlayRuntime();
    private final SceneInstancer instancer = new SceneInstancer();
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Constructor & Builder
    // -------------------------------------------------------------------------

    private MoudServer(Builder builder) {
        this.devMode = builder.devMode;
        this.projectRoot = builder.projectRoot;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean devMode = true;
        private Path projectRoot = Path.of(".");

        public Builder devMode(boolean devMode) {
            this.devMode = devMode;
            return this;
        }

        public Builder projectRoot(Path path) {
            this.projectRoot = path.toAbsolutePath().normalize();
            return this;
        }

        public MoudServer build() {
            return new MoudServer(this);
        }
    }

    // -------------------------------------------------------------------------
    // Static factory from environment variables
    // -------------------------------------------------------------------------

    /**
     * Reads {@code MOUD_MODE} and {@code MOUD_PROJECT_ROOT} env vars.
     * Convenience for the standalone launcher.
     */
    public static Builder fromEnvironment() {
        String mode = System.getenv().getOrDefault("MOUD_MODE", "dev").trim();
        boolean dev = !"player".equalsIgnoreCase(mode);
        String rootEnv = System.getenv().getOrDefault("MOUD_PROJECT_ROOT", ".").trim();
        Path root;
        try {
            root = Path.of(rootEnv.isEmpty() ? "." : rootEnv).toAbsolutePath().normalize();
        } catch (Exception e) {
            root = Path.of(".").toAbsolutePath().normalize();
        }
        DebugLog.info("moud", "mode=" + (dev ? "dev" : "player") + " projectRoot=" + root);
        return builder().devMode(dev).projectRoot(root);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initialises all Moud services and registers event listeners.
     * Call after {@code MinecraftServer.init()} but before {@code minecraftServer.start()}.
     * No {@code PlayerProvider} is set — Moud works with any existing Player subclass.
     */
    public void register(GlobalEventHandler events, InstanceManager instanceManager) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");

        scenes = new ServerScenes(instanceManager);
        mainScene = scenes.ensureDefault("main", "Main");
        project = new ProjectService(projectRoot);
        scripts = new ScriptService(project);
        scriptFiles = new ScriptFileService(project);

        try {
            assets = new AssetService(new FileSystemAssetStore(projectRoot.resolve("assets")), devMode);
            assets.setUploadCompleteCallback(this::onAssetUploaded);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init asset store", e);
        }

        loadScenesFromDisk();
        instancer.syncAll(scenes);

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(mainScene.instance());
            Pos startPos = PlayRuntime.findPlayerStartPos(mainScene);
            event.getPlayer().setRespawnPoint(startPos != null ? startPos : new Pos(0, 64, 0));
        });
        events.addListener(PlayerSpawnEvent.class, event -> onPlayerSpawn(event.getPlayer()));
        events.addListener(PlayerPluginMessageEvent.class, this::onPluginMessage);
        events.addListener(PlayerDisconnectEvent.class, event -> onDisconnect(event.getPlayer()));

        registerCommands();
    }

    /**
     * Registers a 50 ms repeating tick task with Minestom's scheduler.
     * Call after {@link #register} if you want Moud to manage its own tick.
     * Alternatively call {@link #tick()} manually from your own scheduler at 20 Hz.
     */
    public void registerTickTask() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(Duration.ofMillis(50))
                .schedule();
    }

    /**
     * Runs one Moud tick (20 Hz). Called automatically when using
     * {@link #registerTickTask()}, or can be driven by your own scheduler.
     */
    public void tick() {
        scenes.tickAll(TICK_DT_SECONDS);

        Map<UUID, float[]> playerPositions = new HashMap<>();
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
            PlayerState ps = playerStates.get(player.getUuid());
            if (ps == null) {
                continue;
            }
            Session session = ps.session;
            if (session == null) {
                continue;
            }

            if (session.state() == SessionState.CONNECTED && !ps.schemaSent) {
                session.send(Lane.STATE, buildSchemaSnapshot());
                ps.schemaSent = true;
            }

            if (session.state() == SessionState.CONNECTED) {
                sendSceneListIfNeeded(player, ps, session);
                String sceneId = ps.activeSceneId;
                ServerScene scene = scenes.get(sceneId);
                if (scene == null) {
                    scene = mainScene;
                }
                if (scene != null) {
                    float[] followCam = scripts.getFollowCameraForPlayer(scene.sceneId(), player.getUuid());
                    Long playerCamId = followCam == null
                            ? scripts.getActiveCameraForPlayer(scene.sceneId(), player.getUuid())
                            : null;
                    float[] scriptCam = (followCam == null && playerCamId == null)
                            ? scripts.getScriptCameraForPlayer(scene.sceneId(), player.getUuid())
                            : null;
                    playRuntime.tick(player.getUuid(), session, scene, playerCamId, followCam, scriptCam);
                }
            }

            session.tick();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private PlayerState state(Player player) {
        return playerStates.computeIfAbsent(player.getUuid(), id -> new PlayerState());
    }

    private void onPlayerSpawn(Player player) {
        PlayerState ps = state(player);

        player.setRespawnPoint(new Pos(0, 64, 0));
        player.setGameMode(GameMode.ADVENTURE);
        player.sendPluginMessage("minecraft:register", CHANNEL.getBytes(StandardCharsets.UTF_8));

        if (ps.transport == null) {
            ps.transport = new MinestomPlayerTransport(player, CHANNEL);
        }
        if (ps.session == null) {
            Session session = new Session(SessionRole.SERVER, ps.transport);
            session.setServerHelloSupplier(() -> new ServerHello(ProtocolVersions.PROTOCOL_VERSION, devMode));
            session.setLogSink(msg -> DebugLog.debug("session/" + player.getUsername(), msg));
            session.setMessageHandler((lane, message) -> onSessionMessage(player, lane, message));
            session.start();
            ps.session = session;
        }

        ServerScene spawnScene = scenes.get(ps.activeSceneId);
        if (spawnScene == null) spawnScene = mainScene;
        playRuntime.onPlayerSpawn(player, spawnScene);
    }

    private void onPluginMessage(PlayerPluginMessageEvent event) {
        Player player = event.getPlayer();
        PlayerState ps = playerStates.get(player.getUuid());
        if (ps == null) {
            return;
        }
        MinestomPlayerTransport transport = ps.transport;
        if (transport == null) {
            return;
        }
        transport.acceptPluginMessage(event.getIdentifier(), event.getMessage());
        Session session = ps.session;
        if (session != null) {
            session.tick();
        }
    }

    private void onDisconnect(Player player) {
        playerStates.remove(player.getUuid());
        playRuntime.onDisconnect(player.getUuid());
    }

    private void sendSceneListIfNeeded(Player player, PlayerState ps, Session session) {
        long rev = scenes.scenesRevision();
        long last = ps.scenesSentRevision;
        if (last == rev) {
            return;
        }
        ps.scenesSentRevision = rev;
        String active = ps.activeSceneId;
        if (active == null || active.isBlank() || scenes.get(active) == null) {
            active = "main";
            ps.activeSceneId = active;
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
        PlayerState ps = state(player);
        Session session = ps.session;
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
            session.send(Lane.EVENTS, scripts.onListActions(resolvePlayerScene(player), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptActionInvoke request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptActionInvokeAck(request.requestId(), request.nodeId(), false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            session.send(Lane.EVENTS, scripts.onInvokeAction(resolvePlayerScene(player), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileReadRequest request) {
            session.send(Lane.EVENTS, scriptFiles.read(player.getUsername(), devMode, request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileWriteRequest request) {
            session.send(Lane.EVENTS, scriptFiles.write(player.getUsername(), devMode, request));
            return;
        }

        if (lane == Lane.INPUT && message instanceof PlayerInput input) {
            scripts.onPlayerInput(player.getUuid(), input);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof RequestRespawn) {
            String sceneId = ps.activeSceneId;
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
                sid = ps.activeSceneId;
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
            switchPlayerToScene(player, ps, session, scene);
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
                PlayerState epState = playerStates.get(p.getUuid());
                if (epState == null) {
                    continue;
                }
                if (!sid.equals(epState.activeSceneId)) {
                    continue;
                }
                Session s = epState.session;
                if (s == null) {
                    epState.activeSceneId = "main";
                    playRuntime.onSceneChanged(p.getUuid(), "main");
                    continue;
                }
                switchPlayerToScene(p, epState, s, mainScene);
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

            if (next.sceneId().equals(ps.activeSceneId)) {
                return;
            }

            switchPlayerToScene(player, ps, session, next);
            return;
        }

        String sceneId = ps.activeSceneId;
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
            assets.onMessage(player.getUuid(), session, message);
        }
    }

    private ServerScene resolvePlayerScene(Player player) {
        PlayerState ps = playerStates.get(player.getUuid());
        String sceneId = ps != null ? ps.activeSceneId : "main";
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
            PlayerState ps = playerStates.get(p.getUuid());
            if (ps == null) {
                continue;
            }
            if (!fromSceneId.equals(ps.activeSceneId)) {
                continue;
            }
            Session s = ps.session;
            if (s != null) {
                switchPlayerToScene(p, ps, s, target);
            }
        }
    }

    private void switchPlayerToScene(Player player, PlayerState ps, Session session, ServerScene target) {
        if (player == null || ps == null || session == null || target == null) {
            return;
        }
        String targetId = target.sceneId();
        ps.activeSceneId = targetId;
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
