package com.moud.client.init;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.ui.loading.MoudPreloadState;
import com.moud.client.ui.screen.MoudPreloadScreen;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ScriptBundleLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptBundleLoader.class);
    private static final Identifier MOUDPACK_ID = Identifier.of("moud", "dynamic_resources");
    private static final String VEIL_PBR_LIGHT_PACK_ID = "moud_veil_pbr_lights";
    private static final int MAX_BUNDLE_SIZE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_SIZE_BYTES = 64 * 1024 * 1024;
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private final AtomicBoolean resourcesLoaded = new AtomicBoolean(false);
    private final AtomicBoolean serverPackEnabledOnce = new AtomicBoolean(false);
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private final Map<String, ScriptChunkAccumulator> scriptChunkAccumulators = new HashMap<>();

    private String currentResourcesHash = "";
    private ServerInfo.ResourcePackPolicy previousResourcePackPolicy = null;

    public void onJoin(ClientPlayNetworkHandler handler) {
        enableServerResourcePackAutoAccept(handler);
        resourcesLoaded.set(false);
        serverPackEnabledOnce.set(false);
    }

    public void onDisconnect(ClientPlayNetworkHandler handler) {
        restoreServerResourcePackPolicy(handler);
        MoudPreloadState.reset();
        currentResourcesHash = "";
        dynamicPack.set(null);
        scriptChunkAccumulators.clear();
        resourcesLoaded.set(false);
    }

    public void resetState() {
        scriptChunkAccumulators.clear();
        currentResourcesHash = "";
        dynamicPack.set(null);
        resourcesLoaded.set(false);
    }

    public void clear() {
        resetState();
    }

    public void tick(MoudClientMod mod, ClientServiceManager services) {
    }

    public void handleChunk(MoudPackets.SyncClientScriptsChunkPacket packet, MoudClientMod mod, ClientServiceManager services) {
        ScriptChunkAccumulator accumulator = scriptChunkAccumulators.computeIfAbsent(packet.hash(), h -> new ScriptChunkAccumulator(packet.totalChunks()));
        accumulator.accept(packet.chunkIndex(), packet.data());
        if (accumulator.isComplete()) {
            byte[] assembled = accumulator.assemble();
            scriptChunkAccumulators.remove(packet.hash());
            handleCompleteBundle(new MoudPackets.SyncClientScriptsPacket(packet.hash(), assembled), mod, services);
        }
    }

    public void handleCompleteBundle(MoudPackets.SyncClientScriptsPacket packet, MoudClientMod mod, ClientServiceManager services) {
        mod.markAsMoudServer(true);
        scriptChunkAccumulators.remove(packet.hash());

        MinecraftClient.getInstance().execute(() -> {
            if (!MoudPreloadState.isActive()) {
                MoudPreloadState.begin();
            }
            MoudPreloadState.setPhase("Preparing resources...");
            MoudPreloadState.setProgress(0.05f);
            MinecraftClient.getInstance().setScreen(new MoudPreloadScreen());
        });

        ClientAPIService apiService = services.getApiService();
        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:scriptsReceived", packet.hash());
        }

        byte[] scriptPayload = packet.scriptData();
        String expectedHash = packet.hash();

        if (scriptPayload == null || scriptPayload.length == 0) {
            if (expectedHash != null && expectedHash.equals(currentResourcesHash)) {
                LOGGER.info("Server confirmed cached client resources (hash {}).", expectedHash);
                resourcesLoaded.set(true);
                MinecraftClient.getInstance().execute(() -> {
                    MoudPreloadState.setPhase("Using cached resources");
                    MoudPreloadState.finish();
                });
            } else {
                LOGGER.warn("Received empty client bundle with hash {}. Retaining existing resources.", expectedHash);
                MinecraftClient.getInstance().execute(() -> {
                    MoudPreloadState.setPhase("No resources received");
                    MoudPreloadState.finish();
                });
            }
            return;
        }

        if (!currentResourcesHash.isEmpty() && expectedHash != null && expectedHash.equals(currentResourcesHash)) {
            LOGGER.warn("Server resent client bundle with already-applied hash {}. Skipping reload.", expectedHash);
            resourcesLoaded.set(true);
            MinecraftClient.getInstance().execute(() -> {
                MoudPreloadState.setPhase("Resources already loaded");
                MoudPreloadState.finish();
            });
            return;
        }

        if (scriptPayload.length > MAX_BUNDLE_SIZE_BYTES) {
            LOGGER.error("Client bundle from server exceeds safe size ({} bytes > {} bytes).", scriptPayload.length, MAX_BUNDLE_SIZE_BYTES);
            MinecraftClient.getInstance().execute(() -> {
                MoudPreloadState.setPhase("Bundle too large");
                MoudPreloadState.finish();
            });
            return;
        }

        String computedHash = sha256(scriptPayload);
        if (expectedHash != null && !expectedHash.isEmpty() && !expectedHash.equals(computedHash)) {
            LOGGER.error("Client bundle checksum mismatch. Expected {} but computed {}.", expectedHash, computedHash);
            MinecraftClient.getInstance().execute(() -> {
                MoudPreloadState.setPhase("Bundle checksum mismatch");
                MoudPreloadState.finish();
            });
            return;
        }

        if (apiService != null) {
            apiService.rendering.applyDefaultFogIfNeeded();
        }

        currentResourcesHash = expectedHash != null && !expectedHash.isEmpty() ? expectedHash : computedHash;

        CompletableFuture.supplyAsync(() -> {
            MinecraftClient.getInstance().execute(() -> {
                MoudPreloadState.setPhase("Extracting bundle...");
                MoudPreloadState.setProgress(0.15f);
            });
            Map<String, byte[]> scriptsData = new HashMap<>();
            Map<String, byte[]> assetsData = new HashMap<>();

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(scriptPayload))) {
                ZipEntry entry;
                long totalExtracted = 0;
                byte[] buffer = new byte[8192];

                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                    int read;
                    while ((read = zis.read(buffer, 0, buffer.length)) != -1) {
                        totalExtracted += read;
                        if (totalExtracted > MAX_DECOMPRESSED_SIZE_BYTES) {
                            throw new IOException("Decompressed client bundle exceeds safe limit of " + MAX_DECOMPRESSED_SIZE_BYTES + " bytes");
                        }
                        entryBuffer.write(buffer, 0, read);
                    }

                    byte[] data = entryBuffer.toByteArray();
                    String name = entry.getName();
                    if (name.startsWith("scripts/")) {
                        scriptsData.put(name.substring("scripts/".length()), data);
                    } else if (name.startsWith("assets/")) {
                        assetsData.put(name, data);
                        if (name.contains("animation") && name.endsWith(".json")) {
                            String animationName = name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf('.'));
                            LOGGER.debug("Found bundled animation: {} ({})", animationName, name);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Error unpacking script & asset archive", e);
                throw new RuntimeException(e);
            }

            return new Object[] { scriptsData, assetsData };
        }).thenAccept(result -> {
            @SuppressWarnings("unchecked")
            Map<String, byte[]> scriptsData = (Map<String, byte[]>) result[0];
            @SuppressWarnings("unchecked")
            Map<String, byte[]> assetsData = (Map<String, byte[]>) result[1];

            scheduleProcessingWithFrameYield(scriptsData, assetsData, services);
        }).exceptionally(ex -> {
            LOGGER.error("Failed to extract bundle", ex);
            MinecraftClient.getInstance().execute(() -> {
                MoudPreloadState.setPhase("Failed to extract bundle");
                MoudPreloadState.finish();
            });
            return null;
        });
    }

    private void scheduleProcessingWithFrameYield(Map<String, byte[]> scriptsData, Map<String, byte[]> assetsData, ClientServiceManager services) {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        MinecraftClient client = MinecraftClient.getInstance();

        tasks.add(() -> {
            MoudPreloadState.setProgress(0.4f);
        });

        if (assetsData.isEmpty()) {
            tasks.add(() -> {
                LOGGER.info("No assets bundled with client sync payload; loading scripts only (no resource reload).");
                dynamicPack.set(null);
                MoudPreloadState.setPhase("Loading scripts...");
                MoudPreloadState.setProgress(0.85f);
                loadScriptsAsync(scriptsData, services);
            });
        } else {
            tasks.add(() -> {
                MoudPreloadState.setPhase("Preparing resource pack...");
                MoudPreloadState.setProgress(0.45f);
                InMemoryPackResources newPack = new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData);
                dynamicPack.set(newPack);
            });

            tasks.add(() -> {
                MoudPreloadState.setPhase("Scanning packs...");
                MoudPreloadState.setProgress(0.5f);
                ResourcePackManager manager = client.getResourcePackManager();
                manager.scanPacks();
            });

            tasks.add(() -> {
                MoudPreloadState.setPhase("Enabling packs...");
                MoudPreloadState.setProgress(0.55f);
                ResourcePackManager manager = client.getResourcePackManager();
                List<String> enabledPacks = new ArrayList<>(manager.getEnabledIds());
                if (!enabledPacks.contains(MOUDPACK_ID.toString())) {
                    enabledPacks.add(MOUDPACK_ID.toString());
                }
                manager.setEnabledProfiles(enabledPacks);
            });

            tasks.add(() -> {
                MoudPreloadState.setPhase("Reloading resources...");
                MoudPreloadState.setProgress(0.6f);
                client.reloadResourcesConcurrently().thenRunAsync(() -> {
                    LOGGER.info("Resource reload complete. Proceeding with script loading.");
                    if (LOGGER.isDebugEnabled()) {
                        logActiveResourcePacks(client);
                    }
                    MoudPreloadState.setPhase("Loading scripts...");
                    MoudPreloadState.setProgress(0.85f);
                    loadScriptsAsync(scriptsData, services);
                }, client);
            });
        }

        executeTasksWithFrameYield(tasks);
    }

    private void executeTasksWithFrameYield(Queue<Runnable> tasks) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            Runnable task = tasks.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.error("Error executing loading task", e);
                }
                if (!tasks.isEmpty()) {
                    executeTasksWithFrameYield(tasks);
                }
            }
        });
    }

    public void registerResourcePack(Consumer<ResourcePackProfile> profileAdder) {
        InMemoryPackResources pack = dynamicPack.get();
        if (pack != null) {
            ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
                @Nullable
                @Override
                public ResourcePack open(ResourcePackInfo info) {
                    return pack;
                }

                @Nullable
                @Override
                public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                    return pack;
                }
            };

            ResourcePackInfo info = new ResourcePackInfo(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), ResourcePackSource.SERVER, null);
            ResourcePackProfile.Metadata metadata = new ResourcePackProfile.Metadata(Text.of("Moud Dynamic Server Resources"), ResourcePackCompatibility.COMPATIBLE, FeatureSet.empty(), Collections.emptyList());
            ResourcePackPosition position = new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false);
            ResourcePackProfile profile = new ResourcePackProfile(info, factory, metadata, position);

            profileAdder.accept(profile);
        }
    }

    private void loadScriptsOnly(byte[] zippedScriptData, ClientServiceManager services) {
        if (zippedScriptData == null || zippedScriptData.length == 0) {
            LOGGER.warn("Requested to load scripts from empty archive, skipping.");
            return;
        }
        Map<String, byte[]> scriptsData = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zippedScriptData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().startsWith("scripts/")) continue;
                scriptsData.put(entry.getName().substring("scripts/".length()), zis.readAllBytes());
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking scripts from archive for script-only load", e);
        }
        loadScriptsOnly(scriptsData, services);
    }

    private void loadScriptsAsync(Map<String, byte[]> scriptsData, ClientServiceManager services) {
        var runtime = services.getRuntime();
        var apiService = services.getApiService();
        if (runtime == null || apiService == null) {
            LOGGER.error("Scripting runtime or API service is null. Aborting script load.");
            MoudPreloadState.setPhase("Error: Missing runtime");
            MoudPreloadState.finish();
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                runtime.initialize().join();
                apiService.updateScriptingContext(runtime.getContext());

                if (!scriptsData.isEmpty()) {
                    runtime.loadScripts(scriptsData).join();
                } else {
                    LOGGER.info("No scripts to load");
                    MinecraftClient.getInstance().execute(() -> {
                        MoudPreloadState.finish();
                    });
                }

                LOGGER.info("Client scripts loaded successfully");
                resourcesLoaded.set(true);
                MinecraftClient.getInstance().execute(() -> {
                    if (apiService.events != null) {
                        apiService.events.dispatch("core:resourcesReloaded");
                    }
                    LOGGER.info("Scripts loaded. Hash {}.", currentResourcesHash);
                });
            } catch (Exception e) {
                LOGGER.error("A failure occurred during runtime initialization or script loading", e);
                MinecraftClient.getInstance().execute(() -> {
                    MoudPreloadState.setPhase("Error loading scripts");
                    MoudPreloadState.finish();
                });
            }
        });
    }

    private void loadScriptsOnly(Map<String, byte[]> scriptsData, ClientServiceManager services) {
        loadScriptsAsync(scriptsData, services);
    }

    private boolean ensureServerPacksEnabled(MinecraftClient client) {
        if (!MoudClientMod.isOnMoudServer() || serverPackEnabledOnce.get()) {
            return false;
        }
        ResourcePackManager manager = client.getResourcePackManager();
        List<String> enabled = new ArrayList<>(manager.getEnabledIds());
        boolean changed = false;
        boolean serverProfilesPresent = false;
        for (ResourcePackProfile profile : manager.getProfiles()) {
            String id = profile.getInfo().id();
            if ((profile.getInfo().source() == ResourcePackSource.SERVER || id.startsWith("server/")) && !enabled.contains(id)) {
                serverProfilesPresent = true;
                enabled.add(id);
                changed = true;
                LOGGER.info("Enabling server resource pack profile {}", id);
            } else if (profile.getInfo().source() == ResourcePackSource.SERVER || id.startsWith("server/")) {
                serverProfilesPresent = true;
            }
        }
        if (serverProfilesPresent) {
            int veilPbrIndex = enabled.indexOf(VEIL_PBR_LIGHT_PACK_ID);
            if (veilPbrIndex != -1 && veilPbrIndex != enabled.size() - 1) {
                enabled.remove(veilPbrIndex);
                enabled.add(VEIL_PBR_LIGHT_PACK_ID);
                changed = true;
                LOGGER.info("Moved {} to highest priority after server pack changes", VEIL_PBR_LIGHT_PACK_ID);
            }
        }
        if (changed) {
            manager.setEnabledProfiles(enabled);
            serverPackEnabledOnce.set(true);
        }
        return changed;
    }

    private void logActiveResourcePacks(MinecraftClient client) {
        try {
            ResourcePackManager manager = client.getResourcePackManager();
            java.util.Collection<String> enabled = manager.getEnabledIds();
            LOGGER.debug("Active resource packs: {}", enabled);
            if (LOGGER.isTraceEnabled()) {
                manager.getProfiles().forEach(profile -> LOGGER.trace(
                        "Pack profile: id={}, source={}, name={}",
                        profile.getInfo().id(),
                        profile.getInfo().source(),
                        profile.getInfo().title().getString()
                ));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to log active resource packs", e);
        }
    }

    private void enableServerResourcePackAutoAccept(ClientPlayNetworkHandler handler) {
        ServerInfo serverInfo = handler != null ? handler.getServerInfo() : null;
        if (serverInfo == null) {
            previousResourcePackPolicy = null;
            return;
        }
        previousResourcePackPolicy = serverInfo.getResourcePackPolicy();
        if (previousResourcePackPolicy != ServerInfo.ResourcePackPolicy.ENABLED) {
            serverInfo.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.ENABLED);
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                ServerResourcePackLoader loader = client.getServerResourcePackProvider();
                if (loader != null) {
                    loader.acceptAll();
                    LOGGER.info("Auto-accepted pending server resource pack push.");
                } else {
                    LOGGER.warn("Could not auto-accept server resource pack: loader unavailable.");
                }
            });
        }
    }

    private void restoreServerResourcePackPolicy(ClientPlayNetworkHandler handler) {
        if (handler == null) {
            previousResourcePackPolicy = null;
            return;
        }
        ServerInfo serverInfo = handler.getServerInfo();
        if (serverInfo != null && previousResourcePackPolicy != null) {
            serverInfo.setResourcePackPolicy(previousResourcePackPolicy);
            LOGGER.info("Restored previous server resource pack policy after disconnect.");
        }
        previousResourcePackPolicy = null;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            char[] hexChars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                hexChars[i * 2] = HEX_ARRAY[v >>> 4];
                hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static final class ScriptChunkAccumulator {
        private final byte[][] chunks;
        private int received;

        ScriptChunkAccumulator(int totalChunks) {
            this.chunks = new byte[totalChunks][];
            this.received = 0;
        }

        void accept(int index, byte[] data) {
            if (index < 0 || index >= chunks.length) {
                LOGGER.warn("Received out-of-range script chunk {} of {}", index, chunks.length);
                return;
            }
            if (chunks[index] == null) {
                chunks[index] = data;
                received++;
            }
        }

        boolean isComplete() {
            return received == chunks.length;
        }

        byte[] assemble() {
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                totalSize += chunk == null ? 0 : chunk.length;
            }
            byte[] result = new byte[totalSize];
            int pos = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, pos, chunk.length);
                    pos += chunk.length;
                }
            }
            return result;
        }
    }
}
