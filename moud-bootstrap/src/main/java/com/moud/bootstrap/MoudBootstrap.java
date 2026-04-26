package com.moud.bootstrap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class MoudBootstrap implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud-bootstrap");
    private static final String MINIMUM_LOADER_VERSION = "0.12.0";

    private static volatile BootstrapRuntimeManager.PreparedRuntime preparedRuntime;
    private static volatile BootstrapAgentInstaller.InstallationResult agentInstallation;

    @Override
    public void onPreLaunch() {
        try {
            LoaderCompatibility.assertMinimumFabricLoader(MINIMUM_LOADER_VERSION);
            Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            agentInstallation = new BootstrapAgentInstaller().installIfNeeded(gameDir);

            if (agentInstallation != null && agentInstallation.status() != BootstrapAgentInstaller.Status.ALREADY_ACTIVE) {
                LOGGER.info("[moud-bootstrap] Agent install status: {} ({})",
                        agentInstallation.status(), agentInstallation.message());
            }

            BootstrapRuntimeManager.PreparedRuntime prepared = new BootstrapRuntimeManager().prepare(gameDir);
            preparedRuntime = prepared;

            LOGGER.info("[moud-bootstrap] Prepared managed runtime {} ({}, {})",
                    prepared.runtimeJar(), prepared.installedVersion(), prepared.syncStatus());
            if (!prepared.updateStatus().isBlank()) {
                LOGGER.info("[moud-bootstrap] {}", prepared.updateStatus());
            }
        } catch (Exception e) {
            preparedRuntime = null;
            LOGGER.warn("[moud-bootstrap] Bootstrap failed, continuing without managed runtime", e);
        }
    }

    static BootstrapRuntimeManager.PreparedRuntime preparedRuntime() {
        return preparedRuntime;
    }

    static BootstrapAgentInstaller.InstallationResult agentInstallation() {
        return agentInstallation;
    }
}
