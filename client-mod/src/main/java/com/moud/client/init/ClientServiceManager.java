package com.moud.client.init;

import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.cursor.ClientCursorManager;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.display.DisplaySurface;
import com.moud.client.fakeplayer.ClientFakePlayerManager;
import com.moud.client.lighting.ClientLightingService;
import com.moud.client.model.ClientModelManager;
import com.moud.client.movement.ClientMovementTracker;
import com.moud.client.particle.ParticleEmitterSystem;
import com.moud.client.particle.ParticleSystem;
import com.moud.client.player.ClientCameraManager;
import com.moud.client.player.PlayerStateManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.shared.SharedValueManager;
import com.moud.client.ui.ServerUIOverlayManager;
import com.moud.client.ui.UIOverlayManager;
import com.moud.client.ui.animation.UIAnimationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientServiceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientServiceManager.class);

    private final com.moud.client.particle.ParticleSystem particleSystem = new com.moud.client.particle.ParticleSystem(16384);
    private final com.moud.client.particle.ParticleEmitterSystem particleEmitterSystem = new com.moud.client.particle.ParticleEmitterSystem();
    private ClientFakePlayerManager fakePlayerManager;
    private ClientCursorManager cursorManager;
    private ClientAPIService apiService;
    private ClientScriptingRuntime scriptingRuntime;
    private SharedValueManager sharedValueManager;
    private ClientCameraManager clientCameraManager;
    private PlayerStateManager playerStateManager;
    private boolean baseSystemsInitialized = false;
    private boolean runtimeInitialized = false;

    public void initializeBaseSystems() {
        if (baseSystemsInitialized) {
            return;
        }
        this.cursorManager = ClientCursorManager.getInstance();
        this.fakePlayerManager = new ClientFakePlayerManager();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        baseSystemsInitialized = true;
    }

    public void initializeRuntimeServices() {
        if (runtimeInitialized) {
            return;
        }
        LOGGER.info("Initializing Moud services...");
        this.apiService = new ClientAPIService();
        this.scriptingRuntime = new ClientScriptingRuntime(this.apiService);
        this.apiService.setRuntime(this.scriptingRuntime);
        this.sharedValueManager = SharedValueManager.getInstance();
        this.sharedValueManager.initialize();
        this.clientCameraManager = new ClientCameraManager();
        this.playerStateManager = PlayerStateManager.getInstance();
        runtimeInitialized = true;
    }

    public void cleanupRuntimeServices() {
        LOGGER.info("Cleaning up Moud services...");
        if (scriptingRuntime != null) {
            scriptingRuntime.shutdown();
            scriptingRuntime = null;
        }
        if (apiService != null) {
            apiService.cleanup();
            apiService = null;
        }
        if (sharedValueManager != null) {
            sharedValueManager.cleanup();
            sharedValueManager = null;
        }
        if (playerStateManager != null) {
            playerStateManager.reset();
        }
        if (cursorManager != null) {
            cursorManager.clear();
        }
        ServerUIOverlayManager.getInstance().clear();
        UIOverlayManager.getInstance().clear();
        UIAnimationManager.getInstance().clear();
        ClientDisplayManager.getInstance().clear();
        particleEmitterSystem.clear();
        clientCameraManager = null;
        playerStateManager = null;
        runtimeInitialized = false;
    }

    public void shutdown() {
        cleanupRuntimeServices();
    }

    private void tick(MinecraftClient client) {
        ClientLightingService.getInstance().tick();

        if (runtimeInitialized) {
            ClientDisplayManager.getInstance().getDisplays().forEach(DisplaySurface::tick);
        }

        if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
            scriptingRuntime.processGeneralTaskQueue();
            scriptingRuntime.processAnimationFrameQueue();
        }

        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:tick", client.getRenderTickCounter().getTickDelta(true));
        }

        if (apiService != null) {
            apiService.getUpdateManager().tick();
            if (apiService.audio != null) {
                apiService.audio.tick();
            }
            if (apiService.gamepad != null) {
                apiService.gamepad.tick();
            }
        }

        if (clientCameraManager != null) {
            clientCameraManager.tick();
        }

        ClientPlayerModelManager.getInstance().getModels().forEach(com.moud.client.animation.AnimatedPlayerModel::tick);

        com.moud.client.editor.EditorModeManager.getInstance().tick(client);
        ClientMovementTracker.getInstance().tick();
        ClientModelManager.getInstance().getModels().forEach(model -> model.tickSmoothing(1.0f));
    }

    public ClientFakePlayerManager getFakePlayerManager() {
        return fakePlayerManager;
    }

    public ParticleSystem getParticleSystem() {
        return particleSystem;
    }

    public ParticleEmitterSystem getParticleEmitterSystem() {
        return particleEmitterSystem;
    }

    public ClientAPIService getApiService() {
        return apiService;
    }

    public ClientScriptingRuntime getRuntime() {
        return scriptingRuntime;
    }

    public ClientCameraManager getClientCameraManager() {
        return clientCameraManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public ClientCursorManager getCursorManager() {
        return cursorManager;
    }

    public boolean isRuntimeInitialized() {
        return runtimeInitialized;
    }

    public SharedValueManager getSharedValueManager() {
        return sharedValueManager;
    }
}
