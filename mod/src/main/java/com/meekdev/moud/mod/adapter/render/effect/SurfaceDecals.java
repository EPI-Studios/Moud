package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.Decal;
import com.meekdev.moud.core.effect.Texture;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.PartLight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Vector2f;

final class SurfaceDecals {

    private static final List<Decal> ORDER = new ArrayList<>();

    private SurfaceDecals() {}

    static void draw(QuadBatch batch, InstanceTree tree, float partialTick) {
        ORDER.clear();
        for (Decal decal : tree.ofClass(Classes.DECAL)) {
            if (decal.parent() instanceof Part && !decal.texture.isEmpty() && decal.transparency < 1 && !ViewportFrame.inside(decal)) ORDER.add(decal);
        }
        ORDER.sort(Comparator.comparingInt(decal -> decal.zIndex));
        for (Decal decal : ORDER) {
            Part part = (Part) decal.parent();
            CFrame world = Effects.world(part, partialTick);
            GuiLayout.Plane plane = GuiLayout.face(world, part.size, decal.face);
            Vector3 across = plane.right().mul(plane.width() * 0.5);
            Vector3 down = plane.up().mul(-plane.height() * 0.5);
            double u0 = 0;
            double v0 = 0;
            double u1 = 1;
            double v1 = 1;
            if (decal instanceof Texture tiled) {
                u0 = tiled.offsetStudsU / tiled.studsPerTileU;
                v0 = tiled.offsetStudsV / tiled.studsPerTileV;
                u1 = u0 + plane.width() / tiled.studsPerTileU;
                v1 = v0 + plane.height() / tiled.studsPerTileV;
            }
            batch.use(QuadBatch.Layer.SURFACE, EffectTextures.of(decal.texture));
            Vector2f lit = PartLight.of(part, world.position());
            batch.light((int) lit.x | (int) lit.y << 16, 1, 0);
            batch.tint(decal.color, 1 - decal.transparency, shade(plane.right().cross(plane.up())));
            Vector3 centre = plane.centre();
            batch.corner(centre.sub(across).sub(down), u0, v0);
            batch.corner(centre.add(across).sub(down), u1, v0);
            batch.corner(centre.add(across).add(down), u1, v1);
            batch.corner(centre.sub(across).add(down), u0, v1);
        }
    }

    private static double shade(Vector3 normal) {
        double x = Math.abs(normal.x());
        double z = Math.abs(normal.z());
        if (Math.abs(normal.y()) > Math.max(x, z)) return normal.y() > 0 ? 1 : 0.5;
        return z > x ? 0.8 : 0.6;
    }
}
