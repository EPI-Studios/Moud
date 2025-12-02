package com.moud.server.physics.mesh;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.Triangle;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.physics.PhysicsService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChunkMesher {

    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ChunkMesher.class,
            LogContext.builder().put("subsystem", "physics-mesh").build()
    );
    private static final BlockFace[] BLOCK_FACES = BlockFace.values();

    private ChunkMesher() {
    }

    public static @Nullable BodyCreationSettings createChunk(Chunk chunk) {
        return createChunk(chunk, false);
    }

    public static @Nullable BodyCreationSettings createChunk(Chunk chunk, boolean fullBlocksOnly) {
        int minY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).minY();
        int maxY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).maxY();

        return generateChunkCollisionObject(chunk, minY, maxY, fullBlocksOnly);
    }

    private static @Nullable BodyCreationSettings generateChunkCollisionObject(Chunk chunk, int minY, int maxY, boolean fullBlocksOnly) {
        List<Face> faces = getChunkFaces(chunk, minY, maxY, fullBlocksOnly);

        if (faces.isEmpty()) {
            LOGGER.debug(LogContext.builder()
                    .put("chunkX", chunk.getChunkX())
                    .put("chunkZ", chunk.getChunkZ())
                    .build(), "Chunk ({}, {}) has no collision faces - skipping physics mesh", chunk.getChunkX(), chunk.getChunkZ());
            return null;
        }

        List<Triangle> triangles = new ArrayList<>(faces.size() * 2);
        for (Face face : faces) {
            face.addTris(triangles);
        }

        LOGGER.debug(LogContext.builder()
                        .put("chunkX", chunk.getChunkX())
                        .put("chunkZ", chunk.getChunkZ())
                        .put("faces", faces.size())
                        .put("triangles", triangles.size())
                        .build(), "Created collision mesh for chunk ({}, {}) with {} faces ({} triangles)",
                chunk.getChunkX(), chunk.getChunkZ(), faces.size(), triangles.size());

        MeshShapeSettings shapeSettings = new MeshShapeSettings(triangles);

        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setMotionType(EMotionType.Static)
                .setObjectLayer(PhysicsService.LAYER_STATIC)
                .setShape(shapeSettings.create().get());

        return bodySettings;
    }

    private static List<Face> getChunkFaces(Chunk chunk, int minY, int maxY, boolean fullBlocksOnly) {
        int bottomY = maxY;
        int topY = minY;

        List<Section> sections = chunk.getSections();
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (isEmpty(section)) continue;
            int chunkBottom = minY + i * Chunk.CHUNK_SECTION_SIZE;
            int chunkTop = chunkBottom + Chunk.CHUNK_SECTION_SIZE;

            if (bottomY > chunkBottom) {
                bottomY = chunkBottom;
            }
            if (topY < chunkTop) {
                topY = chunkTop;
            }
        }

        int estimatedCapacity = Math.max(200, (topY - bottomY) * 4);
        List<Face> finalFaces = new ArrayList<>(estimatedCapacity);

        for (int y = bottomY; y < topY; y++) {
            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    List<Face> faces = getFaces(chunk, x, y, z, fullBlocksOnly);
                    if (faces == null) continue;
                    finalFaces.addAll(faces);
                }
            }
        }

        return finalFaces;
    }

    private static @Nullable List<Face> getFaces(Chunk chunk, int x, int y, int z, boolean fullBlocksOnly){
        Block block = chunk.getBlock(x, y, z, Block.Getter.Condition.TYPE);

        if (block.isAir() || block.isLiquid()) return null;

        Shape shape = block.registry().collisionShape();
        Point relStart = shape.relativeStart();
        Point relEnd = shape.relativeEnd();

        boolean blockIsFull = isFullShape(relStart, relEnd);
        if (fullBlocksOnly && !blockIsFull) return null;

        List<Face> faces = new ArrayList<>(6);

        var blockX = chunk.getChunkX() * Chunk.CHUNK_SIZE_X + x;
        var blockZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE_Z + z;

        for (BlockFace blockFace : BLOCK_FACES) {

            if (blockIsFull) {
                var dir = blockFace.toDirection();
                var neighbourBlock = chunk.getBlock(x + dir.normalX(), y + dir.normalY(), z + dir.normalZ(), Block.Getter.Condition.TYPE);
                if (isFullBlock(neighbourBlock)) {
                    continue;
                }
            }

            Face face = new Face(
                    blockFace,
                    blockFace == BlockFace.EAST ? relEnd.x() : relStart.x(),
                    blockFace == BlockFace.TOP ? relEnd.y() : relStart.y(),
                    blockFace == BlockFace.SOUTH ? relEnd.z() : relStart.z(),
                    blockFace == BlockFace.WEST ? relStart.x() : relEnd.x(),
                    blockFace == BlockFace.BOTTOM ? relStart.y() : relEnd.y(),
                    blockFace == BlockFace.NORTH ? relStart.z() : relEnd.z(),
                    blockX,
                    y,
                    blockZ
            );

            if (!face.isEdge()) {
                faces.add(face);
                continue;
            }

            if (!blockIsFull) {
                var dir = blockFace.toDirection();
                var neighbourBlock = chunk.getBlock(x + dir.normalX(), y + dir.normalY(), z + dir.normalZ(), Block.Getter.Condition.TYPE);

                if (!isFull(neighbourBlock)) {
                    faces.add(face);
                }
            } else {

                faces.add(face);
            }
        }

        return faces.isEmpty() ? null : faces;
    }

    private static boolean isFullShape(Point relStart, Point relEnd) {
        return relStart.x() == 0.0 && relStart.y() == 0.0 && relStart.z() == 0.0 &&
                relEnd.x() == 1.0 && relEnd.y() == 1.0 && relEnd.z() == 1.0;
    }

    private static boolean isFullBlock(Block block) {
        if (block.isAir() || block.isLiquid()) return false;
        Shape shape = block.registry().collisionShape();
        return isFullShape(shape.relativeStart(), shape.relativeEnd());
    }

    private static boolean isFull(Block block) {
        if (block.isAir() || block.isLiquid()) return false;

        Shape shape = block.registry().collisionShape();
        Point relStart = shape.relativeStart();
        Point relEnd = shape.relativeEnd();

        return relStart.x() == 0.0 && relStart.y() == 0.0 && relStart.z() == 0.0 &&
                relEnd.x() == 1.0 && relEnd.y() == 1.0 && relEnd.z() == 1.0;
    }

    private static boolean isEmpty(Section section) {
        return section.blockPalette().count() == 0;
    }
}
