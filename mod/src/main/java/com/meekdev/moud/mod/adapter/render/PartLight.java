package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;

public final class PartLight {

    private static final Vector2f FULL = new Vector2f(240f, 240f);

    private static final Map<Instance, Vector2f> BY_PART = new WeakHashMap<>();

    private PartLight() {}

    public static Vector2f of(Instance part, Vector3 at) {
        Vector2f known = BY_PART.get(part);
        return known != null ? known : FULL;
    }

    public static void refresh(Instance part, Vector3 at) {
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
