package com.moud.client.collision;

import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.physics.ClientPhysicsBodyIds;
import com.moud.client.physics.ClientPhysicsWorld;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public final class ClientChunkCollisionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientChunkCollisionManager.class);
    private static final ClientChunkCollisionManager INSTANCE = new ClientChunkCollisionManager();
    private static final int MAX_REQUESTS_PER_TICK = Integer.getInteger("moud.physics.chunkCollisionRequestsPerTick", 2);
    private static final ExecutorService CHUNK_COLLISION_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Moud-ChunkCollisionLoader");
        t.setDaemon(true);
        return t;
    });

    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> requestedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final ArrayDeque<Long> requestQueue = new ArrayDeque<>();
    private final Map<Long, Integer> loadSequence = new ConcurrentHashMap<>();

    private ClientChunkCollisionManager() {
    }

    public static ClientChunkCollisionManager getInstance() {
        return INSTANCE;
    }

    public void onChunkLoad(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        loadedChunks.add(key);
        enqueueChunkCollisionRequest(chunkX, chunkZ);
    }

    public void onChunkUnload(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        loadedChunks.remove(key);
        requestedChunks.remove(key);
        queuedChunks.remove(key);
        synchronized (requestQueue) {
            requestQueue.remove(key);
        }
        loadSequence.remove(key);
        removeChunkBody(chunkX, chunkZ);
    }

    public void tick() {
        if (MAX_REQUESTS_PER_TICK <= 0) {
            return;
        }
        for (int i = 0; i < MAX_REQUESTS_PER_TICK; i++) {
            Long keyObj;
            synchronized (requestQueue) {
                keyObj = requestQueue.pollFirst();
            }
            if (keyObj == null) {
                break;
            }
            long key = keyObj;
            queuedChunks.remove(key);
            if (!loadedChunks.contains(key) || requestedChunks.contains(key)) {
                continue;
            }
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            requestedChunks.add(key);
            ClientPacketWrapper.sendToServer(new MoudPackets.RequestChunkCollisionPacket(chunkX, chunkZ));
        }
    }

    public void handleChunkCollisionPacket(MoudPackets.ChunkCollisionPacket packet) {
        if (packet == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client != null ? client.world : null;
        if (world == null) {
            return;
        }

        int chunkX = packet.chunkX();
        int chunkZ = packet.chunkZ();

        long key = packChunkKey(chunkX, chunkZ);
        if (!loadedChunks.contains(key)) {
            removeChunkBody(chunkX, chunkZ);
            return;
        }

        if (packet.remove()) {
            removeChunkBody(chunkX, chunkZ);
            return;
        }
        int seq = loadSequence.merge(key, 1, Integer::sum);
        byte[] vertsBytes = packet.compressedVertices();
        byte[] idxBytes = packet.compressedIndices();
        if (vertsBytes == null || idxBytes == null) {
            return;
        }
        CHUNK_COLLISION_EXECUTOR.execute(() -> {
            float[] vertices = gunzipFloats(vertsBytes);
            int[] indices = gunzipInts(idxBytes);
            if (vertices == null || indices == null) {
                LOGGER.warn(
                        "Failed to apply chunk collision mesh for ({}, {}): missing/invalid data",
                        chunkX,
                        chunkZ
                );
                return;
            }

            ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
            if (!physics.isInitialized()) {
                return;
            }
            var shape = ClientPhysicsWorld.buildMeshShape(vertices, indices, new Vector3f(1, 1, 1));
            if (shape == null) {
                return;
            }

            MinecraftClient.getInstance().execute(() -> {
                if (!loadedChunks.contains(key) || loadSequence.getOrDefault(key, 0) != seq) {
                    return;
                }
                if (!physics.isInitialized()) {
                    return;
                }
                long bodyId = ClientPhysicsBodyIds.chunk(chunkX, chunkZ);
                physics.addStaticMeshShape(bodyId, shape, new Vector3f(0, 0, 0), new Quaternionf());
            });
        });
    }

    public void clear() {
        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized()) {
            for (long key : loadedChunks) {
                int chunkX = unpackChunkX(key);
                int chunkZ = unpackChunkZ(key);
                physics.removeStaticMesh(ClientPhysicsBodyIds.chunk(chunkX, chunkZ));
            }
        }
        loadedChunks.clear();
        requestedChunks.clear();
        queuedChunks.clear();
        synchronized (requestQueue) {
            requestQueue.clear();
        }
        loadSequence.clear();
    }

    private void enqueueChunkCollisionRequest(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        if (requestedChunks.contains(key) || !loadedChunks.contains(key) || !queuedChunks.add(key)) {
            return;
        }
        synchronized (requestQueue) {
            requestQueue.addLast(key);
        }
    }

    private void removeChunkBody(int chunkX, int chunkZ) {
        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (!physics.isInitialized()) {
            return;
        }
        physics.removeStaticMesh(ClientPhysicsBodyIds.chunk(chunkX, chunkZ));
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
    }

    private static int unpackChunkX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackChunkZ(long key) {
        return (int) key;
    }

    private static float[] gunzipFloats(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = gis.readAllBytes();
            if (buf.length % 4 != 0) {
                return null;
            }
            int count = buf.length / 4;
            float[] out = new float[count];
            int o = 0;
            for (int i = 0; i < buf.length; i += 4) {
                int bits = (buf[i] & 0xFF)
                        | ((buf[i + 1] & 0xFF) << 8)
                        | ((buf[i + 2] & 0xFF) << 16)
                        | ((buf[i + 3] & 0xFF) << 24);
                out[o++] = Float.intBitsToFloat(bits);
            }
            return out;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int[] gunzipInts(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = gis.readAllBytes();
            if (buf.length % 4 != 0) {
                return null;
            }
            int count = buf.length / 4;
            int[] out = new int[count];
            int o = 0;
            for (int i = 0; i < buf.length; i += 4) {
                int v = (buf[i] & 0xFF)
                        | ((buf[i + 1] & 0xFF) << 8)
                        | ((buf[i + 2] & 0xFF) << 16)
                        | ((buf[i + 3] & 0xFF) << 24);
                out[o++] = v;
            }
            return out;
        } catch (IOException ignored) {
            return null;
        }
    }
}
