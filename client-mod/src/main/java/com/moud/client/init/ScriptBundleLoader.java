package com.moud.client.init;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.resources.InMemoryPackResources;
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
    private static final long JOIN_TIMEOUT_MS = 10000;
    private static final int MAX_BUNDLE_SIZE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_SIZE_BYTES = 64 * 1024 * 1024;
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private final AtomicBoolean resourcesLoaded = new AtomicBoolean(false);
    private final AtomicBoolean waitingForResources = new AtomicBoolean(false);
    private final AtomicBoolean serverPackEnabledOnce = new AtomicBoolean(false);
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private final Map<String, ScriptChunkAccumulator> scriptChunkAccumulators = new HashMap<>();
    private final Queue<Consumer<ClientPlayNetworkHandler>> deferredPacketHandlers = new ConcurrentLinkedQueue<>();

    private String currentResourcesHash = "";
    private long joinTime = -1L;
    private GameJoinS2CPacket pendingGameJoinPacket = null;
    private ServerInfo.ResourcePackPolicy previousResourcePackPolicy = null;

    public void onJoin(ClientPlayNetworkHandler handler) {
        enableServerResourcePackAutoAccept(handler);
        resourcesLoaded.set(false);
        waitingForResources.set(true);
        joinTime = System.currentTimeMillis();
        serverPackEnabledOnce.set(false);
    }

    public void onDisconnect(ClientPlayNetworkHandler handler) {
        restoreServerResourcePackPolicy(handler);
        joinTime = -1L;
        currentResourcesHash = "";
        dynamicPack.set(null);
        scriptChunkAccumulators.clear();
        pendingGameJoinPacket = null;
        deferredPacketHandlers.clear();
        waitingForResources.set(false);
        resourcesLoaded.set(false);
    }

    public void resetState() {
        scriptChunkAccumulators.clear();
        currentResourcesHash = "";
        dynamicPack.set(null);
        pendingGameJoinPacket = null;
        resourcesLoaded.set(false);
        waitingForResources.set(false);
    }

    public void clear() {
        resetState();
    }

    public void tick(MoudClientMod mod, ClientServiceManager services) {
        if (joinTime != -1L && waitingForResources.get() && System.currentTimeMillis() - joinTime > JOIN_TIMEOUT_MS) {
            LOGGER.warn("Timed out waiting for Moud server handshake. Assuming non-Moud server and proceeding with join.");
            waitingForResources.set(false);
            resourcesLoaded.set(true);
            joinTime = -1L;
            services.cleanupRuntimeServices();
            mod.markAsMoudServer(false);
        }
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
        joinTime = -1L;
        scriptChunkAccumulators.remove(packet.hash());

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
                waitingForResources.set(false);
                processPendingGameJoinPacket();
            } else {
                LOGGER.warn("Received empty client bundle with hash {}. Retaining existing resources.", expectedHash);
            }
            return;
        }

        if (!currentResourcesHash.isEmpty() && expectedHash != null && expectedHash.equals(currentResourcesHash)) {
            LOGGER.warn("Server resent client bundle with already-applied hash {}. Skipping reload.", expectedHash);
            resourcesLoaded.set(true);
            waitingForResources.set(false);
            processPendingGameJoinPacket();
            return;
        }

        if (scriptPayload.length > MAX_BUNDLE_SIZE_BYTES) {
            LOGGER.error("Client bundle from server exceeds safe size ({} bytes > {} bytes).", scriptPayload.length, MAX_BUNDLE_SIZE_BYTES);
            waitingForResources.set(false);
            return;
        }

        waitingForResources.set(true);

        String computedHash = sha256(scriptPayload);
        if (expectedHash != null && !expectedHash.isEmpty() && !expectedHash.equals(computedHash)) {
            LOGGER.error("Client bundle checksum mismatch. Expected {} but computed {}.", expectedHash, computedHash);
            waitingForResources.set(false);
            return;
        }

        if (apiService != null) {
            apiService.rendering.applyDefaultFogIfNeeded();
        }

        currentResourcesHash = expectedHash != null && !expectedHash.isEmpty() ? expectedHash : computedHash;

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
                        LOGGER.info("Loading animation: {} from path: {}", animationName, name);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking script & asset archive", e);
            waitingForResources.set(false);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (assetsData.isEmpty()) {
                LOGGER.info("No assets bundled with client sync payload; assuming server resource pack covers assets.");
                logActiveResourcePacks(client);
                if (ensureServerPacksEnabled(client)) {
                    client.reloadResources().thenRunAsync(() -> {
                        logActiveResourcePacks(client);
                        dynamicPack.set(null);
                        loadScriptsOnly(scriptsData, services);
                        resourcesLoaded.set(true);
                        waitingForResources.set(false);
                        if (services.getApiService() != null && services.getApiService().events != null) {
                            services.getApiService().events.dispatch("core:resourcesReloaded");
                        }
                        processPendingGameJoinPacket();
                        LOGGER.info("Scripts loaded without bundled assets after enabling server pack. Hash {}.", currentResourcesHash);
                    }, client);
                } else {
                    dynamicPack.set(null);
                    loadScriptsOnly(scriptsData, services);
                    resourcesLoaded.set(true);
                    waitingForResources.set(false);
                    if (services.getApiService() != null && services.getApiService().events != null) {
                        services.getApiService().events.dispatch("core:resourcesReloaded");
                    }
                    processPendingGameJoinPacket();
                    LOGGER.info("Scripts loaded without bundled assets. Hash {}.", currentResourcesHash);
                }
                return;
            }

            InMemoryPackResources newPack = new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData);
            dynamicPack.set(newPack);

            ResourcePackManager manager = client.getResourcePackManager();
            manager.scanPacks();

            List<String> enabledPacks = new ArrayList<>(manager.getEnabledIds());
            if (!enabledPacks.contains(MOUDPACK_ID.toString())) {
                enabledPacks.add(MOUDPACK_ID.toString());
            }

            manager.setEnabledProfiles(enabledPacks);

            client.reloadResources().thenRunAsync(() -> {
                LOGGER.info("Resource reload complete. Proceeding with script loading.");
                logActiveResourcePacks(client);
                loadScriptsOnly(scriptsData, services);
                resourcesLoaded.set(true);
                waitingForResources.set(false);
                if (services.getApiService() != null && services.getApiService().events != null) {
                    services.getApiService().events.dispatch("core:resourcesReloaded");
                }
                processPendingGameJoinPacket();
                LOGGER.info("Dynamic resources enabled and scripts loaded. Hash {}.", currentResourcesHash);
            }, client);
        });
    }

    public boolean shouldBlockJoin() {
        return false;
    }

    public void processPendingGameJoinPacket() {
        if (pendingGameJoinPacket != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getNetworkHandler() != null) {
                LOGGER.info("Processing delayed GameJoin packet now that resources are loaded.");
                client.getNetworkHandler().onGameJoin(pendingGameJoinPacket);
                pendingGameJoinPacket = null;
                drainDeferredPackets(client.getNetworkHandler());
            } else {
                LOGGER.error("Could not process pending game join packet: network handler is null!");
                client.disconnect();
            }
        }
    }

    public void setPendingGameJoinPacket(GameJoinS2CPacket packet) {
        this.pendingGameJoinPacket = packet;
        LOGGER.info("Moud client is delaying game join, waiting for server resources...");
    }

    public void enqueuePostJoinPacket(Consumer<ClientPlayNetworkHandler> consumer) {
        deferredPacketHandlers.offer(consumer);
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

    private void loadScriptsOnly(Map<String, byte[]> scriptsData, ClientServiceManager services) {
        var runtime = services.getRuntime();
        var apiService = services.getApiService();
        if (runtime == null || apiService == null) {
            LOGGER.error("Scripting runtime or API service is null. Aborting script load.");
            return;
        }
        runtime.initialize().thenCompose(v -> {
                    apiService.updateScriptingContext(runtime.getContext());
                    if (!scriptsData.isEmpty()) {
                        return runtime.loadScripts(scriptsData);
                    }
                    return CompletableFuture.completedFuture(null);
                }).thenRun(() -> LOGGER.info("Client scripts loaded successfully"))
                .exceptionally(t -> {
                    LOGGER.error("A failure occurred during runtime initialization or script loading", t);
                    return null;
                });
    }

    private boolean ensureServerPacksEnabled(MinecraftClient client) {
        if (!MoudClientMod.isOnMoudServer() || serverPackEnabledOnce.get()) {
            return false;
        }
        ResourcePackManager manager = client.getResourcePackManager();
        List<String> enabled = new ArrayList<>(manager.getEnabledIds());
        boolean changed = false;
        for (ResourcePackProfile profile : manager.getProfiles()) {
            String id = profile.getInfo().id();
            if ((profile.getInfo().source() == ResourcePackSource.SERVER || id.startsWith("server/")) && !enabled.contains(id)) {
                enabled.add(id);
                changed = true;
                LOGGER.info("Enabling server resource pack profile {}", id);
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
            LOGGER.info("Active resource packs: {}", enabled);
            manager.getProfiles().forEach(profile -> LOGGER.info("Pack profile: id={}, source={}, name={}", profile.getInfo().id(), profile.getInfo().source(), profile.getInfo().title().getString()));
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

    private void drainDeferredPackets(ClientPlayNetworkHandler handler) {
        Consumer<ClientPlayNetworkHandler> task;
        while ((task = deferredPacketHandlers.poll()) != null) {
            try {
                task.accept(handler);
            } catch (Exception ex) {
                LOGGER.error("Failed to replay deferred packet", ex);
            }
        }
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
