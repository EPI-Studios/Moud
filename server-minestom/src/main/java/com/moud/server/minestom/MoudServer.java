package com.moud.server.minestom;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetManifest;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.wire.WireMessages;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.assets.AssetService;
import com.moud.server.minestom.assets.FileSystemAssetStore;
import com.moud.server.minestom.engine.SceneInstancer;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.runtime.PlayRuntime;
import com.moud.server.minestom.scripting.ScriptFileService;
import com.moud.server.minestom.scripting.ScriptService;
import com.moud.server.minestom.util.DebugLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceManager;

public final class MoudServer {

    private static final String CHANNEL = "moud:engine";
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

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

    private SceneStorage sceneStorage;
    private PlayModeManager playModeManager;
    private MessageRouter messageRouter;
    private PlayerConnectionHandler connections;
    private ServerTickLoop tickLoop;

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

    public void register(GlobalEventHandler events, InstanceManager instanceManager) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        logResolvedPaths();

        scenes = new ServerScenes(instanceManager);
        mainScene = scenes.ensureDefault("main", "Main");
        project = new ProjectService(projectRoot);

        Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
        PlayerMessageSink playerMessageSink = (uuid, lane, message) -> {
            if (uuid == null || message == null) return;
            PlayerState ps = playerStates.get(uuid);
            if (ps == null) return;
            Session session = ps.session;
            if (session == null || session.state() != SessionState.CONNECTED) return;
            session.send(lane, message);
        };

        scripts = new ScriptService(project, playerMessageSink);
        scriptFiles = new ScriptFileService(project);
        sceneStorage = new SceneStorage(projectRoot, scenes, instancer, scripts);
        playModeManager = new PlayModeManager(scenes, mainScene, scripts, instancer, playRuntime, playerMessageSink);

        try {
            assets = new AssetService(new FileSystemAssetStore(projectRoot.resolve("assets")), devMode);
            assets.setUploadCompleteCallback(sceneStorage::onAssetUploaded);
            logAssetStoreState();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init asset store", e);
        }

        sceneStorage.loadScenesFromDisk();
        instancer.syncAll(scenes);

        messageRouter = new MessageRouter(
                devMode,
                project,
                scripts,
                scriptFiles,
                assets,
                scenes,
                mainScene,
                instancer,
                sceneStorage,
                playModeManager,
                playerStates
        );
        connections = new PlayerConnectionHandler(
                CHANNEL,
                devMode,
                scenes,
                mainScene,
                playModeManager,
                messageRouter,
                playerStates
        );
        tickLoop = new ServerTickLoop(TICK_DT_SECONDS, scenes, scripts, playModeManager, connections);

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(mainScene.instance());
            Pos startPos = PlayRuntime.findPlayerStartPos(mainScene);
            event.getPlayer().setRespawnPoint(startPos != null ? startPos : new Pos(0, 64, 0));
        });
        events.addListener(PlayerSpawnEvent.class, event -> connections.onPlayerSpawn(event.getPlayer()));
        events.addListener(PlayerPluginMessageEvent.class, connections::onPluginMessage);
        events.addListener(PlayerDisconnectEvent.class, event -> connections.onDisconnect(event.getPlayer()));

        registerCommands();
    }

    private void logResolvedPaths() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path assetRoot = projectRoot.resolve("assets").toAbsolutePath().normalize();
        Path scenesRoot = projectRoot.resolve("scenes").toAbsolutePath().normalize();
        Path scriptsRoot = projectRoot.resolve("scripts").toAbsolutePath().normalize();
        DebugLog.info("moud", "cwd=" + cwd);
        DebugLog.info("moud", "projectRoot=" + projectRoot);
        DebugLog.debug("moud", "javaVersion=" + Runtime.version().feature()
                + " vm=" + System.getProperty("java.vm.name", ""));
        DebugLog.info("moud", "assetRoot=" + assetRoot + " exists=" + Files.isDirectory(assetRoot));
        DebugLog.info("moud", "scenesRoot=" + scenesRoot + " exists=" + Files.isDirectory(scenesRoot));
        DebugLog.info("moud", "scriptsRoot=" + scriptsRoot + " exists=" + Files.isDirectory(scriptsRoot));
    }

    private void logAssetStoreState() {
        AssetService assetService = assets;
        if (assetService == null) {
            return;
        }
        AssetManifest manifest = assetService.store().manifest();
        int count = manifest == null || manifest.entries() == null ? 0 : manifest.entries().size();
        DebugLog.info("assets", "manifestEntries=" + count);
    }

    public void registerTickTask() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(Duration.ofMillis(50))
                .schedule();
    }

    public void tick() {
        ServerTickLoop loop = tickLoop;
        if (loop != null) {
            loop.tick();
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
            scene.engine().bumpPhysicsRevision();
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

}
