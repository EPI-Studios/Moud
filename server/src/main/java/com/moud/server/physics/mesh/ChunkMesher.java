package com.moud.server.physics.mesh;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Triangle;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
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
        return createChunkMesh(chunk, fullBlocksOnly).bodySettings();
    }

    public record ChunkCollisionMesh(
            @Nullable BodyCreationSettings bodySettings,
            @Nullable float[] vertices,
            @Nullable int[] indices
    ) {
    }

    public static ChunkCollisionMesh createChunkMesh(Chunk chunk, boolean fullBlocksOnly) {
        int minY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).minY();
        int maxY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).maxY();

        return generateChunkCollisionMesh(chunk, minY, maxY, fullBlocksOnly);
    }

    /**
     * Collects all exposed block faces for a chunk for debugging or ad-hoc raycasts.
     */
    public static List<Face> collectFaces(Chunk chunk, boolean fullBlocksOnly) {
        int minY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).minY();
        int maxY = MinecraftServer.getDimensionTypeRegistry().get(chunk.getInstance().getDimensionType()).maxY();
        return getChunkFaces(chunk, minY, maxY, fullBlocksOnly);
    }

    private static ChunkCollisionMesh generateChunkCollisionMesh(Chunk chunk, int minY, int maxY, boolean fullBlocksOnly) {
        List<Face> faces = getChunkFaces(chunk, minY, maxY, fullBlocksOnly);

        if (faces.isEmpty()) {
            LOGGER.debug(LogContext.builder()
                    .put("chunkX", chunk.getChunkX())
                    .put("chunkZ", chunk.getChunkZ())
                    .build(), "Chunk ({}, {}) has no collision faces - skipping physics mesh", chunk.getChunkX(), chunk.getChunkZ());
            return new ChunkCollisionMesh(null, null, null);
        }

        int faceCount = faces.size();
        int triCount = faceCount * 2;
        float[] vertices = new float[triCount * 9];
        int[] indices = new int[triCount * 3];

        List<Triangle> triangles = new ArrayList<>(triCount);
        int vCursor = 0;
        int iCursor = 0;
        int vertexIndex = 0;

        for (Face face : faces) {
            if (face == null) {
                continue;
            }

            float p1x;
            float p1y;
            float p1z;
            float p2x;
            float p2y;
            float p2z;
            float p3x;
            float p3y;
            float p3z;
            float p4x;
            float p4y;
            float p4z;

            switch (face.blockFace()) {
                case TOP -> {
                    p1x = face.minX() + face.blockX();
                    p1y = face.maxY() + face.blockY();
                    p1z = face.minZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.maxY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.maxX() + face.blockX();
                    p3y = face.maxY() + face.blockY();
                    p3z = face.maxZ() + face.blockZ();
                    p4x = face.minX() + face.blockX();
                    p4y = face.maxY() + face.blockY();
                    p4z = face.maxZ() + face.blockZ();
                }
                case BOTTOM -> {
                    p1x = face.maxX() + face.blockX();
                    p1y = face.maxY() + face.blockY();
                    p1z = face.maxZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.maxY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.minX() + face.blockX();
                    p3y = face.maxY() + face.blockY();
                    p3z = face.minZ() + face.blockZ();
                    p4x = face.minX() + face.blockX();
                    p4y = face.maxY() + face.blockY();
                    p4z = face.maxZ() + face.blockZ();
                }
                case WEST -> {
                    p1x = face.maxX() + face.blockX();
                    p1y = face.minY() + face.blockY();
                    p1z = face.minZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.maxY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.maxX() + face.blockX();
                    p3y = face.maxY() + face.blockY();
                    p3z = face.maxZ() + face.blockZ();
                    p4x = face.maxX() + face.blockX();
                    p4y = face.minY() + face.blockY();
                    p4z = face.maxZ() + face.blockZ();
                }
                case EAST -> {
                    p1x = face.maxX() + face.blockX();
                    p1y = face.maxY() + face.blockY();
                    p1z = face.maxZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.maxY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.maxX() + face.blockX();
                    p3y = face.minY() + face.blockY();
                    p3z = face.minZ() + face.blockZ();
                    p4x = face.maxX() + face.blockX();
                    p4y = face.minY() + face.blockY();
                    p4z = face.maxZ() + face.blockZ();
                }
                case SOUTH -> {
                    p1x = face.maxX() + face.blockX();
                    p1y = face.maxY() + face.blockY();
                    p1z = face.minZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.minY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.minX() + face.blockX();
                    p3y = face.minY() + face.blockY();
                    p3z = face.minZ() + face.blockZ();
                    p4x = face.minX() + face.blockX();
                    p4y = face.maxY() + face.blockY();
                    p4z = face.minZ() + face.blockZ();
                }
                case NORTH -> {
                    p1x = face.minX() + face.blockX();
                    p1y = face.minY() + face.blockY();
                    p1z = face.minZ() + face.blockZ();
                    p2x = face.maxX() + face.blockX();
                    p2y = face.minY() + face.blockY();
                    p2z = face.minZ() + face.blockZ();
                    p3x = face.maxX() + face.blockX();
                    p3y = face.maxY() + face.blockY();
                    p3z = face.minZ() + face.blockZ();
                    p4x = face.minX() + face.blockX();
                    p4y = face.maxY() + face.blockY();
                    p4z = face.minZ() + face.blockZ();
                }
                default -> {
                    continue;
                }
            }

            // Match Face.addTris ordering.
            triangles.add(new Triangle(
                    new Vec3(p3x, p3y, p3z),
                    new Vec3(p2x, p2y, p2z),
                    new Vec3(p1x, p1y, p1z)
            ));
            triangles.add(new Triangle(
                    new Vec3(p1x, p1y, p1z),
                    new Vec3(p4x, p4y, p4z),
                    new Vec3(p3x, p3y, p3z)
            ));

            // Triangle 1: p3, p2, p1
            vertices[vCursor++] = p3x;
            vertices[vCursor++] = p3y;
            vertices[vCursor++] = p3z;
            vertices[vCursor++] = p2x;
            vertices[vCursor++] = p2y;
            vertices[vCursor++] = p2z;
            vertices[vCursor++] = p1x;
            vertices[vCursor++] = p1y;
            vertices[vCursor++] = p1z;

            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;

            // Triangle 2: p1, p4, p3
            vertices[vCursor++] = p1x;
            vertices[vCursor++] = p1y;
            vertices[vCursor++] = p1z;
            vertices[vCursor++] = p4x;
            vertices[vCursor++] = p4y;
            vertices[vCursor++] = p4z;
            vertices[vCursor++] = p3x;
            vertices[vCursor++] = p3y;
            vertices[vCursor++] = p3z;

            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;
        }

        if (vertexIndex < 3) {
            return new ChunkCollisionMesh(null, null, null);
        }

        if (vCursor != vertices.length) {
            float[] trimmed = new float[vCursor];
            System.arraycopy(vertices, 0, trimmed, 0, vCursor);
            vertices = trimmed;
        }
        if (iCursor != indices.length) {
            int[] trimmed = new int[iCursor];
            System.arraycopy(indices, 0, trimmed, 0, iCursor);
            indices = trimmed;
        }

        LOGGER.debug(LogContext.builder()
                        .put("chunkX", chunk.getChunkX())
                        .put("chunkZ", chunk.getChunkZ())
                        .put("faces", faces.size())
                        .put("triangles", triangles.size())
                        .build(), "Created collision mesh for chunk ({}, {}) with {} faces ({} triangles)",
                chunk.getChunkX(), chunk.getChunkZ(), faces.size(), triangles.size());

        MeshShapeSettings shapeSettings = new MeshShapeSettings(triangles);
        ShapeResult shapeResult = shapeSettings.create();
        if (shapeResult.hasError()) {
            LOGGER.warn(
                    LogContext.builder()
                            .put("chunkX", chunk.getChunkX())
                            .put("chunkZ", chunk.getChunkZ())
                            .put("error", shapeResult.getError())
                            .build(),
                    "Failed to create chunk mesh shape: {}",
                    shapeResult.getError()
            );
            return new ChunkCollisionMesh(null, null, null);
        }
        ConstShape shape = shapeResult.get();
        if (shape == null) {
            LOGGER.warn(
                    LogContext.builder()
                            .put("chunkX", chunk.getChunkX())
                    .put("chunkZ", chunk.getChunkZ())
                    .build(),
                    "Failed to create chunk mesh shape (null shape result)"
            );
            return new ChunkCollisionMesh(null, null, null);
        }

        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setMotionType(EMotionType.Static)
                .setObjectLayer(PhysicsService.LAYER_STATIC)
                .setShape(shape);

        return new ChunkCollisionMesh(bodySettings, vertices, indices);
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
