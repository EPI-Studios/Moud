package com.moud.plugin.api;

import com.moud.plugin.api.services.ClientService;
import com.moud.plugin.api.services.CommandService;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.LightingService;
import com.moud.plugin.api.services.ModelService;
import com.moud.plugin.api.services.NetworkService;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.plugin.api.services.PlayerService;
import com.moud.plugin.api.services.RenderingController;
import com.moud.plugin.api.services.WorldService;
import com.moud.plugin.api.services.SchedulerService;
import com.moud.plugin.api.services.SceneService;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Runtime handle provided to plugins during load/enable. Exposes service singletons and the plugin data directory.
 */
public interface PluginContext {
    /**
     * Metadata for the active plugin instance.
     */
    PluginDescription description();
    /**
     * Logger tagged with the plugin id.
     */
    Logger logger();
    /**
     * Plugin-specific persistent storage root.
     */
    Path dataDirectory();
    /**
     * Scheduler for delayed or repeating tasks.
     */
    SchedulerService scheduler();
    /**
     * Model management entrypoint.
     */
    ModelService models();
    /**
     * Lighting control entrypoint.
     */
    LightingService lighting();
    /**
     * Event bus for server and client events.
     */
    EventService events();
    /**
     * Network helpers.
     */
    NetworkService network();
    /**
     * Scene graph utilities for editor/runtime integration.
     */
    SceneService scenes();
    /**
     * Command registration helper.
     */
    CommandService commands();
    /**
     * Player lookup and messaging utilities.
     */
    PlayerService players();
    /**
     * Client capability and cursor helpers.
     */
    ClientService clients();
    /**
     * Physics body controller and queries.
     */
    PhysicsController physics();
    /**
     * Rendering toggle hooks.
     */
    RenderingController rendering();
    /**
     * World loading and block utilities.
     */
    WorldService world();
}
