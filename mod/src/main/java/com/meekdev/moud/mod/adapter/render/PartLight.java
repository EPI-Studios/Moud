package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vec3;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;

// where a part sits in minecraft's light map, as the coordinates its own shaders use
//
// the packed value is a block level in the low half and a sky level in the high half, and the light
// map turns that pair into the colour of the hour. reading it is a chunk lookup, so it is read once
// a tick per part rather than once a frame: light changes at the speed of a torch being placed, and
// nothing about it needs to keep up with a frame
public final class PartLight {

    private static final Vector2f FULL = new Vector2f(240f, 240f);

    private static final Map<Instance, Vector2f> BY_PART = new IdentityHashMap<>();

    private PartLight() {}

    public static Vector2f of(Instance part, Vec3 at) {
        Vector2f known = BY_PART.get(part);
        return known != null ? known : FULL;
    }

    // once a tick, for what is on screen. a part that never moves keeps the value it had until the
    // light around it changes, which is what this is refreshed for
    public static void refresh(Instance part, Vec3 at) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        int packed = LevelRenderer.getLightCoords(level,
                BlockPos.containing(at.x(), at.y(), at.z()));
        BY_PART.computeIfAbsent(part, p -> new Vector2f())
                .set(packed & 0xFFFF, (packed >> 16) & 0xFFFF);
    }

    public static void forget(Instance part) {
        BY_PART.remove(part);
    }

    public static void clear() {
        BY_PART.clear();
    }
}
