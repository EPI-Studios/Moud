package com.moud.server.plugin.context;

import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.PluginDescription;
import com.moud.plugin.api.services.ClientService;
import com.moud.plugin.api.services.CommandService;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.LightingService;
import com.moud.plugin.api.services.ModelService;
import com.moud.plugin.api.services.NetworkService;
import com.moud.plugin.api.services.CameraService;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.plugin.api.services.PlayerService;
import com.moud.plugin.api.services.RenderingController;
import com.moud.plugin.api.services.ParticleService;
import com.moud.plugin.api.services.SchedulerService;
import com.moud.plugin.api.services.SceneService;
import com.moud.plugin.api.services.WorldService;
import com.moud.server.plugin.PluginEventBus;
import com.moud.server.plugin.impl.ClientServiceImpl;
import com.moud.server.plugin.impl.CommandServiceImpl;
import com.moud.server.plugin.impl.EventServiceImpl;
import com.moud.server.plugin.impl.LightingServiceImpl;
import com.moud.server.plugin.impl.ModelServiceImpl;
import com.moud.server.plugin.impl.NetworkServiceImpl;
import com.moud.server.plugin.impl.CameraServiceImpl;
import com.moud.server.plugin.impl.PhysicsControllerImpl;
import com.moud.server.plugin.impl.PlayerServiceImpl;
import com.moud.server.plugin.impl.RenderingControllerImpl;
import com.moud.server.plugin.impl.ParticleServiceImpl;
import com.moud.server.plugin.impl.SchedulerServiceImpl;
import com.moud.server.plugin.impl.SceneServiceImpl;
import com.moud.server.plugin.impl.WorldServiceImpl;
import com.moud.server.plugin.impl.ParticleServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginContextImpl implements PluginContext {
    private final com.moud.server.plugin.core.PluginDescription description;
    private final Logger logger;
    private final Path dataDirectory;
    private final SchedulerServiceImpl schedulerService;
    private final ModelServiceImpl modelService;
    private final LightingServiceImpl lightingService;
    private final EventServiceImpl eventService;
    private final NetworkServiceImpl networkService;
    private final SceneServiceImpl sceneService;
    private final CommandServiceImpl commandService;
    private final PlayerServiceImpl playerService;
    private final ClientServiceImpl clientService;
    private final PhysicsControllerImpl physicsController;
    private final CameraServiceImpl cameraService;
    private final RenderingControllerImpl renderingController;
    private final WorldServiceImpl worldService;

    private final ParticleServiceImpl particleService;

    public PluginContextImpl(com.moud.server.plugin.core.PluginDescription description,
                             Path projectRoot,
                             Object owner) {
        this.description = description;
        this.logger = LoggerFactory.getLogger("MoudPlugin-" + description.id);
        this.dataDirectory = projectRoot.resolve(".moud")
                .resolve("plugins-data")
                .resolve(description.id);
        ensureDirectories();

        this.schedulerService = new SchedulerServiceImpl(description.id, logger);
        this.modelService = new ModelServiceImpl(logger);
        this.lightingService = new LightingServiceImpl(logger);
        this.eventService = new EventServiceImpl(PluginEventBus.getInstance(), owner);
        this.networkService = new NetworkServiceImpl(logger);
        this.sceneService = new SceneServiceImpl(projectRoot, logger);
        this.commandService = new CommandServiceImpl(logger);
        this.playerService = new PlayerServiceImpl();
        this.clientService = new ClientServiceImpl(logger);
        this.physicsController = new PhysicsControllerImpl();
        this.cameraService = new CameraServiceImpl();
        this.renderingController = new RenderingControllerImpl();
        this.worldService = new WorldServiceImpl();
        this.particleService = new ParticleServiceImpl(logger);
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create plugin data directory " + dataDirectory, e);
        }
    }

    public void shutdown() {
        schedulerService.cancelAll();
        modelService.shutdown();
        lightingService.shutdown();
        eventService.shutdown();
        commandService.unregisterAll();
    }

    @Override
    public PluginDescription description() {
        return new PluginDescription(
                description.id,
                description.name,
                description.version,
                description.description
        );
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public SchedulerService scheduler() {
        return schedulerService;
    }

    @Override
    public ModelService models() {
        return modelService;
    }

    @Override
    public LightingService lighting() {
        return lightingService;
    }

    @Override
    public EventService events() {
        return eventService;
    }

    @Override
    public NetworkService network() {
        return networkService;
    }

    @Override
    public SceneService scenes() {
        return sceneService;
    }

    @Override
    public CommandService commands() {
        return commandService;
    }

    @Override
    public PlayerService players() {
        return playerService;
    }

    @Override
    public ClientService clients() {
        return clientService;
    }

    @Override
    public PhysicsController physics() {
        return physicsController;
    }

    @Override
    public CameraService cameras() {
        return cameraService;
    }

    @Override
    public RenderingController rendering() {
        return renderingController;
    }

    @Override
    public WorldService world() {
        return worldService;
    }

    @Override
    public ParticleService particles() {
        return particleService;
    }
}
