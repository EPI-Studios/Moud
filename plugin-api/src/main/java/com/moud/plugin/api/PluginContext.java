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

public interface PluginContext {
    PluginDescription description();
    Logger logger();
    Path dataDirectory();
    SchedulerService scheduler();
    ModelService models();
    LightingService lighting();
    EventService events();
    NetworkService network();
    SceneService scenes();
    CommandService commands();
    PlayerService players();
    ClientService clients();
    PhysicsController physics();
    RenderingController rendering();
    WorldService world();
}
