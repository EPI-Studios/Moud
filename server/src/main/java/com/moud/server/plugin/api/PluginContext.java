package com.moud.server.plugin.api;

import com.moud.plugin.api.services.SceneService;
import com.moud.plugin.api.services.CommandService;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.LightingService;
import com.moud.plugin.api.services.ModelService;
import com.moud.plugin.api.services.NetworkService;
import com.moud.plugin.api.services.SchedulerService;

import org.slf4j.Logger;

public interface PluginContext {
    String pluginId();
    Logger logger();
    SchedulerService scheduler();
    ModelService models();
    LightingService lighting();
    EventService events();
    NetworkService network();
    SceneService scenes();
    CommandService commands();
}
