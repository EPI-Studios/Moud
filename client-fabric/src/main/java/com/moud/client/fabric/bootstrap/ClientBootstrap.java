package com.moud.client.fabric.bootstrap;

import com.moud.core.update.ArtifactDownloader;
import com.moud.core.update.GitHubReleaseResolver;
import com.moud.core.update.ReleaseKeys;
import com.moud.core.update.UpdateOrchestrator;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientBootstrap implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud-bootstrap");

    private static final String GITHUB_OWNER = "EPI-Studios";
    private static final String GITHUB_REPO = "Moud";
    private static final String TARGET = "client";

    private static final long UPDATE_CHECK_TIMEOUT_SECONDS = 10;
    private static final long UPDATE_APPLY_TIMEOUT_SECONDS = 120;

    @Override
    public void onPreLaunch() {
        if (Boolean.getBoolean("moud.client.wrapper")) {
            LOGGER.info("[moud-bootstrap] External client launcher detected; skipping in-mod updater");
            return;
        }

        LOGGER.info("[moud-bootstrap] Starting update check...");

        Path baseDir = resolveBaseDir();
        UpdateOrchestrator orchestrator;
        boolean updateAppliedThisLaunch = false;
        String syncedVersion = "";

        try {
            Files.createDirectories(baseDir);
            String publicKey = ReleaseKeys.loadDefaultPublicKeyPem();
            GitHubReleaseResolver resolver = new GitHubReleaseResolver(GITHUB_OWNER, GITHUB_REPO, publicKey);
            ArtifactDownloader downloader = new ArtifactDownloader();
            orchestrator = new UpdateOrchestrator(TARGET, baseDir, resolver, downloader);
        } catch (Exception e) {
            LOGGER.error("[moud-bootstrap] Failed to initialize updater, skipping", e);
            return;
        }

        try {
            var checkFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return orchestrator.check(false);
                } catch (Exception e) {
                    LOGGER.warn("[moud-bootstrap] Update check failed: {}", e.getMessage());
                    return null;
                }
            });

            var check = checkFuture.get(UPDATE_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (check != null && check.updateAvailable()) {
                LOGGER.info("[moud-bootstrap] Update available: {} -> {}",
                        check.currentVersion(), check.latestVersion());
                AtomicInteger lastLoggedMilestone = new AtomicInteger(-1);

                var applyFuture = CompletableFuture.supplyAsync(() ->
                        orchestrator.apply(check.manifest(), (downloaded, total) -> {
                            if (total > 0) {
                                int pct = (int) (downloaded * 100 / total);
                                int milestone = Math.min(100, (pct / 25) * 25);
                                if (milestone >= 0 && lastLoggedMilestone.getAndSet(milestone) != milestone) {
                                    LOGGER.info("[moud-bootstrap] Downloading... {}%", milestone);
                                }
                            }
                        }));

                var result = applyFuture.get(UPDATE_APPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (result.success()) {
                    LOGGER.info("[moud-bootstrap] Update applied: {}", result.version());
                    updateAppliedThisLaunch = true;
                    syncedVersion = result.version();
                } else {
                    LOGGER.warn("[moud-bootstrap] Update failed: {}", result.error());
                }
            } else if (check != null) {
                LOGGER.info("[moud-bootstrap] Up to date (version: {})", check.currentVersion());
                syncedVersion = check.currentVersion();
            }
        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.warn("[moud-bootstrap] Update check timed out, continuing with existing version");
        } catch (Exception e) {
            LOGGER.warn("[moud-bootstrap] Update check failed, continuing with existing version: {}",
                    e.getMessage());
        }

        try {
            Path engineDir = orchestrator.currentEngineDir();
            if (engineDir != null) {
                ClientModSync.SyncResult sync = ClientModSync.syncInstalledVersion(engineDir);
                switch (sync.status()) {
                    case SYNCED -> {
                        LOGGER.info("[moud-bootstrap] Synced installed client jar to {} for the next launch",
                                sync.targetJar());
                        if (updateAppliedThisLaunch) {
                            LOGGER.info("[moud-bootstrap] Version {} will load after you restart Minecraft", syncedVersion);
                        }
                    }
                    case FAILED -> LOGGER.warn("[moud-bootstrap] Failed to sync installed client jar: {}",
                            sync.message());
                    case SKIPPED -> LOGGER.info("[moud-bootstrap] Installed client jar not synced: {}",
                            sync.message());
                    case UP_TO_DATE -> {
                        if (!syncedVersion.isBlank()) {
                            LOGGER.info("[moud-bootstrap] Bootstrap jar already matches installed version {}",
                                    syncedVersion);
                        }
                    }
                }
            } else {
                LOGGER.info("[moud-bootstrap] No engine version installed yet");
            }
        } catch (Exception e) {
            LOGGER.error("[moud-bootstrap] Client jar sync failed", e);
        }
    }

    private static Path resolveBaseDir() {
        Path gameDir = Path.of(".").toAbsolutePath();
        return gameDir.resolve(".moud").resolve("client");
    }
}
