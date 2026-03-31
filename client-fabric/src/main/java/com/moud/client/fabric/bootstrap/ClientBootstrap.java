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

/**
 * Fabric preLaunch entrypoint that checks for engine updates before the game starts.
 * <p>
 * The update check runs with a timeout — if the network is slow or unreachable,
 * the game continues with whatever version is already installed.
 * <p>
 * Flow:
 * <ol>
 *   <li>Check GitHub Releases for a new version (with timeout)</li>
 *   <li>Download + verify + extract if update available</li>
 *   <li>Inject the engine jar into Knot's classloader</li>
 * </ol>
 */
public final class ClientBootstrap implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud-bootstrap");

    private static final String GITHUB_OWNER = "moudproject";
    private static final String GITHUB_REPO = "Moud";
    private static final String TARGET = "client";

    /** Max time to spend on the update check before giving up. */
    private static final long UPDATE_CHECK_TIMEOUT_SECONDS = 10;
    /** Max time to spend downloading + applying an update. */
    private static final long UPDATE_APPLY_TIMEOUT_SECONDS = 120;

    @Override
    public void onPreLaunch() {
        LOGGER.info("[moud-bootstrap] Starting update check...");

        Path baseDir = resolveBaseDir();
        UpdateOrchestrator orchestrator;

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

        // Run the update check with a timeout so a dead network doesn't block the game
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

                var applyFuture = CompletableFuture.supplyAsync(() ->
                        orchestrator.apply(check.manifest(), (downloaded, total) -> {
                            if (total > 0) {
                                int pct = (int) (downloaded * 100 / total);
                                if (pct % 25 == 0) {
                                    LOGGER.info("[moud-bootstrap] Downloading... {}%", pct);
                                }
                            }
                        }));

                var result = applyFuture.get(UPDATE_APPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (result.success()) {
                    LOGGER.info("[moud-bootstrap] Update applied: {}", result.version());
                } else {
                    LOGGER.warn("[moud-bootstrap] Update failed: {}", result.error());
                }
            } else if (check != null) {
                LOGGER.info("[moud-bootstrap] Up to date (version: {})", check.currentVersion());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.warn("[moud-bootstrap] Update check timed out, continuing with existing version");
        } catch (Exception e) {
            LOGGER.warn("[moud-bootstrap] Update check failed, continuing with existing version: {}",
                    e.getMessage());
        }

        // Always try to inject whatever engine version is installed
        try {
            Path engineDir = orchestrator.currentEngineDir();
            if (engineDir != null) {
                KnotClassLoaderAccess.injectEngine(engineDir);
            } else {
                LOGGER.info("[moud-bootstrap] No engine version installed yet");
            }
        } catch (Exception e) {
            LOGGER.error("[moud-bootstrap] Engine injection failed", e);
        }
    }

    private static Path resolveBaseDir() {
        Path gameDir = Path.of(".").toAbsolutePath();
        return gameDir.resolve(".moud").resolve("client");
    }
}
