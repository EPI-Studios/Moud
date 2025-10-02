//package com.moud.server.bridge;
//
//import com.moud.api.math.Vector3;
//import com.moud.server.MoudEngine;
//import com.moud.server.proxy.PlayerModelProxy;
//import fr.ghostrider584.axiom.AxiomMinestom;
//import fr.ghostrider584.axiom.event.AxiomManipulateEntityEvent;
//import fr.ghostrider584.axiom.event.AxiomRemoveEntityEvent;
//import fr.ghostrider584.axiom.marker.MarkerTags;
//import fr.ghostrider584.axiom.restrictions.AxiomPermissions;
//import net.minestom.server.MinecraftServer;
//import net.minestom.server.coordinate.Pos;
//import net.minestom.server.entity.Entity;
//import net.minestom.server.entity.EntityType;
//import net.minestom.server.entity.Player;
//import net.minestom.server.event.Event;
//import net.minestom.server.event.EventNode;
//import net.minestom.server.event.player.PlayerBlockPlaceEvent;
//import net.minestom.server.event.player.PlayerSpawnEvent;
//import net.minestom.server.instance.Instance;
//import net.minestom.server.instance.block.Block;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//
//public class AxiomBridgeService {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(AxiomBridgeService.class);
//    private final MoudEngine engine;
//    private final EventNode<Event> eventNode;
//
//    private final Map<UUID, Long> markerToModelId = new ConcurrentHashMap<>();
//    private final Map<Long, UUID> modelIdToMarker = new ConcurrentHashMap<>();
//
//    private static AxiomBridgeService instance;
//
//    public AxiomBridgeService(MoudEngine engine) {
//        this.engine = engine;
//        this.eventNode = EventNode.all("moud-axiom-bridge");
//        instance = this;
//    }
//
//    public static AxiomBridgeService getInstance() {
//        return instance;
//    }
//
//    public void initialize() {
//        LOGGER.info("Initializing Axiom Bridge Service...");
//
//        // Initialize the core Axiom library
//        AxiomMinestom.initialize();
//
//        // Set up permissions - allow OP players (level 4) to use Axiom
//        AxiomPermissions.setPermissionPredicate((player, permission) -> player.getPermissionLevel() >= 4);
//
//        // Register event listeners
//        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
//        registerListeners();
//
//        LOGGER.info("Axiom Bridge Service initialized.");
//    }
//
//    private void registerListeners() {
//        // When a player with Axiom joins, spawn existing gizmos for them
//        eventNode.addListener(PlayerSpawnEvent.class, this::onPlayerSpawn);
//
//        // Listen for Axiom's entity modification events (move, rotate, scale)
//        eventNode.addListener(AxiomManipulateEntityEvent.class, this::onAxiomManipulateEntity);
//
//        // Listen for Axiom's entity removal event
//        eventNode.addListener(AxiomRemoveEntityEvent.class, this::onAxiomRemoveEntity);
//
//        // Use a special block placement to create new PlayerModelProxies
//        eventNode.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlaceForCreation);
//    }
//
//    public void createGizmoForModel(PlayerModelProxy model) {
//        Instance instance = MoudEngine.getInstance().getScriptingAPI().getWorld().getInstance();
//        if (instance == null) {
//            LOGGER.error("Cannot create Axiom gizmo: Default instance is null.");
//            return;
//        }
//
//        var marker = new Entity(EntityType.MARKER);
//        marker.setNoGravity(true);
//
//        // Define the gizmo's appearance (a 1x2x1 box)
//        marker.setTag(MarkerTags.NAME, "§ePlayer Model #" + model.getModelId());
//        marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList("~-0.5", "~0.0", "~-0.5"));
//        marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList("~0.5", "~2.0", "~0.5"));
//        marker.setTag(MarkerTags.LINE_ARGB, MarkerTags.argbToInt(255, 255, 215, 0)); // Gold color
//        marker.setTag(MarkerTags.FACE_ARGB, MarkerTags.argbToInt(40, 255, 215, 0));
//        marker.setTag(MarkerTags.LINE_THICKNESS, 2.0f);
//
//        Vector3 pos = model.getPosition();
//        marker.setInstance(instance, new Pos(pos.x, pos.y, pos.z));
//
//        // Store the mapping
//        markerToModelId.put(marker.getUuid(), model.getModelId());
//        modelIdToMarker.put(model.getModelId(), marker.getUuid());
//
//        LOGGER.debug("Created Axiom gizmo (Marker UUID: {}) for PlayerModel (ID: {})", marker.getUuid(), model.getModelId());
//    }
//
//    public void removeGizmoForModel(PlayerModelProxy model) {
//        UUID markerUuid = modelIdToMarker.remove(model.getModelId());
//        if (markerUuid != null) {
//            markerToModelId.remove(markerUuid);
//            Instance instance = MoudEngine.getInstance().getScriptingAPI().getWorld().getInstance();
//            Entity marker = instance.getEntityByUuid(markerUuid);
//            if (marker != null) {
//                marker.remove();
//                LOGGER.debug("Removed Axiom gizmo for PlayerModel (ID: {})", model.getModelId());
//            }
//        }
//    }
//
//    private void onPlayerSpawn(PlayerSpawnEvent event) {
//        if (!event.isFirstSpawn()) return;
//        Player player = event.getPlayer();
//
//        // Send spawn packets for all our gizmos to the newly joined player
//        Instance instance = MoudEngine.getInstance().getScriptingAPI().getWorld().getInstance();
//        for (UUID markerUuid : markerToModelId.keySet()) {
//            Entity marker = instance.getEntityByUuid(markerUuid);
//            if (marker != null) {
//                marker.addViewer(player);
//            }
//        }
//    }
//
//    private void onAxiomManipulateEntity(AxiomManipulateEntityEvent event) {
//        Entity marker = event.getEntity();
//        UUID markerUuid = marker.getUuid();
//        Long modelId = markerToModelId.get(markerUuid);
//
//        if (modelId == null) return; // Not a gizmo we manage
//
//        PlayerModelProxy model = PlayerModelProxy.getById(modelId);
//        if (model == null) {
//            LOGGER.warn("Axiom tried to modify a gizmo for a non-existent PlayerModel (ID: {})", modelId);
//            return;
//        }
//
//        // The Axiom library's handler has already teleported the marker entity.
//        // We just need to read its new state and apply it to our PlayerModelProxy.
//        Pos newPos = marker.getPosition();
//        model.setPosition(new Vector3(newPos.x(), newPos.y(), newPos.z()));
//        model.setRotation(newPos.yaw(), newPos.pitch());
//
//        LOGGER.debug("Player {} modified PlayerModel #{} via Axiom", event.getPlayer().getUsername(), modelId);
//    }
//
//    private void onAxiomRemoveEntity(AxiomRemoveEntityEvent event) {
//        UUID uuid = event.getEntity().getUuid();
//        Long modelId = markerToModelId.get(uuid);
//        if (modelId != null) {
//            PlayerModelProxy model = PlayerModelProxy.getById(modelId);
//            if (model != null) {
//                model.remove(); // This will also trigger removeGizmoForModel and cleanup
//                LOGGER.info("Player {} removed PlayerModel #{} via Axiom", event.getPlayer().getUsername(), modelId);
//            }
//        }
//    }
//
//    private void onBlockPlaceForCreation(PlayerBlockPlaceEvent event) {
//        // Use Gold Block as the "Create Player Model" tool
//        if (event.getBlock() != Block.GOLD_BLOCK) {
//            return;
//        }
//
//        // Check if the player has Axiom permissions
//        if (event.getPlayer().getPermissionLevel() >= 4) {
//            event.setCancelled(true); // Don't actually place the block
//
//            Pos pos = new Pos(event.getBlockPosition()).add(0.5, 0, 0.5);
//            new PlayerModelProxy(new Vector3(pos.x(), pos.y(), pos.z()), "");
//
//            LOGGER.info("Player {} created a new PlayerModel via Axiom at {}", event.getPlayer().getUsername(), pos);
//        }
//    }
//
//    public void shutdown() {
//        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
//        LOGGER.info("Axiom Bridge Service shut down.");
//    }
//}