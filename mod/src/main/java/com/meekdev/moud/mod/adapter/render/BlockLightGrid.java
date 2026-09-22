package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.mod.adapter.gl.Textures;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.lwjgl.BufferUtils;

/**
 * The block and sky light around the camera, as a volume the part shaders read by world position.
 * Minecraft only knows light per block, so a part used to take one sample for its whole surface and
 * came out flat; this lets a wall be dark indoors and lit outdoors.
 */
public final class BlockLightGrid {

    private static final int SIZE = 32;
    private static final int MOVED = 4;
    private static final int SLICES_PER_FRAME = 8;
    private static final float LEVELS = 16f;

    private static final ByteBuffer PAIRS = BufferUtils.createByteBuffer(SIZE * SIZE * SIZE * 2);
    private static final float[] PLACED = new float[4];

    private static int texture;
    private static int originX;
    private static int originY;
    private static int originZ;
    private static int scannedY = SIZE;
    private static boolean ready;

    private BlockLightGrid() {}

    public static void frame() {
        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null || client.gameRenderer == null) return;
        BlockPos eye = client.gameRenderer.getMainCamera().blockPosition();
        int wantedX = eye.getX() - SIZE / 2;
        int wantedY = eye.getY() - SIZE / 2;
        int wantedZ = eye.getZ() - SIZE / 2;
        if (!ready || far(wantedX - originX) || far(wantedY - originY) || far(wantedZ - originZ)) {
            originX = wantedX;
            originY = wantedY;
            originZ = wantedZ;
            scannedY = 0;
        }
        if (scannedY < SIZE) scan(level);
    }

    private static boolean far(int moved) {
        return Math.abs(moved) >= MOVED;
    }

    // a few horizontal slices a frame, so a walk never pays for the whole volume at once
    private static void scan(Level level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int until = Math.min(SIZE, scannedY + SLICES_PER_FRAME);
        for (int y = scannedY; y < until; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    at.set(originX + x, originY + y, originZ + z);
                    int packed = LevelRenderer.getLightCoords(level, at);
                    int slot = ((y * SIZE + z) * SIZE + x) * 2;
                    PAIRS.put(slot, (byte) Math.min(255, packed & 0xFFFF));
                    PAIRS.put(slot + 1, (byte) Math.min(255, (packed >> 16) & 0xFFFF));
                }
            }
        }
        scannedY = until;
        if (scannedY < SIZE) return;
        PAIRS.position(0).limit(PAIRS.capacity());
        if (texture == 0) texture = Textures.volume(SIZE, PAIRS);
        else Textures.updateVolume(texture, SIZE, PAIRS);
        ready = true;
    }

    public static int glId() {
        return texture;
    }

    /** Where the volume sits and how wide it is, for the shader to turn a world position into a lookup. */
    public static float[] placed() {
        PLACED[0] = originX;
        PLACED[1] = originY;
        PLACED[2] = originZ;
        PLACED[3] = ready ? SIZE : 0;
        return PLACED;
    }

    public static float levels() {
        return LEVELS;
    }

    public static void forget() {
        ready = false;
        scannedY = SIZE;
    }
}
