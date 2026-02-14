package com.moud.server.minestom.engine.csg;

import com.moud.core.csg.CsgVoxelizer;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import com.moud.server.minestom.engine.Engine;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CsgBlockWriter {
    private final InstanceContainer instance;
    private final Engine engine;
    private long lastAppliedRevision = Long.MIN_VALUE;
    private Map<Long, Block> lastBlocks = new HashMap<>();

    public CsgBlockWriter(InstanceContainer instance, Engine engine) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    private static int roundToInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.round(Float.parseFloat(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            float v = Float.parseFloat(value.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String defaulted(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static long pack(int x, int y, int z) {
        long lx = x & 0x1FFFFF;
        long ly = y & 0x3FFFFF;
        long lz = z & 0x1FFFFF;
        return lx | (ly << 21) | (lz << (21 + 22));
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    private static int unpackX(long key) {
        return signExtend((int) (key & 0x1FFFFF), 21);
    }

    private static int unpackY(long key) {
        return signExtend((int) ((key >>> 21) & 0x3FFFFF), 22);
    }

    private static int unpackZ(long key) {
        return signExtend((int) ((key >>> (21 + 22)) & 0x1FFFFF), 21);
    }

    public void tick() {
        long rev = engine.csgRevision();
        if (rev == lastAppliedRevision) {
            return;
        }
        lastAppliedRevision = rev;
        applySnapshot();
    }

    private int applySnapshot() {
        Map<Long, Block> next = new HashMap<>();
        SceneTree tree = engine.sceneTree();
        collect(tree.root(), next);

        int removed = 0;
        for (Map.Entry<Long, Block> entry : lastBlocks.entrySet()) {
            long key = entry.getKey();
            if (!next.containsKey(key)) {
                removed++;
                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                instance.setBlock(x, y, z, Block.AIR);
            }
        }

        int changed = 0;
        for (Map.Entry<Long, Block> entry : next.entrySet()) {
            long key = entry.getKey();
            Block block = entry.getValue();
            Block prev = lastBlocks.get(key);
            if (prev != null && prev.equals(block)) {
                continue;
            }
            changed++;
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            instance.setBlock(x, y, z, block);
        }

        lastBlocks = next;
        return next.size();
    }

    private void collect(Node node, Map<Long, Block> out) {
        if ("CSGBlock".equals(engine.nodeTypes().typeIdFor(node))) {
            emitCsgBlock(node, out);
        }
        for (Node child : node.children()) {
            collect(child, out);
        }
    }

    private void emitCsgBlock(Node node, Map<Long, Block> out) {
        int x = roundToInt(node.getProperty("x"), 0);
        int y = roundToInt(node.getProperty("y"), 42);
        int z = roundToInt(node.getProperty("z"), 0);
        int sx = Math.max(1, roundToInt(node.getProperty("sx"), 1));
        int sy = Math.max(1, roundToInt(node.getProperty("sy"), 1));
        int sz = Math.max(1, roundToInt(node.getProperty("sz"), 1));
        float rxDeg = parseFloat(node.getProperty("rx"), 0.0f);
        float ryDeg = parseFloat(node.getProperty("ry"), 0.0f);
        float rzDeg = parseFloat(node.getProperty("rz"), 0.0f);
        String blockId = defaulted(node.getProperty("block"), "minecraft:stone");

        Block block = Block.fromNamespaceId(blockId);
        if (block == null) {
            block = Block.STONE;
        }

        Block finalBlock = block;
        CsgVoxelizer.forEachVoxel(x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg, (xx, yy, zz) -> out.put(pack(xx, yy, zz), finalBlock));
    }
}
