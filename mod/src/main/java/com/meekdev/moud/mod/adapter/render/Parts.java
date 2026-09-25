package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.ibl.Ibl;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePacker;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShading;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.ClientScene;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.meekdev.moud.mod.adapter.render.effect.EffectTextures;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class Parts {

    private static final Identifier[] STILL = ids("parts_still");
    private static final Identifier[] MOVING = ids("parts_moving");
    private static final Identifier[] GLASS = ids("parts_glass");

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector4f TINT = new Vector4f();

    private static final Map<Textured, List<Part>> PACKED = new HashMap<>();
    private static final Set<Integer> SHARP = new HashSet<>();

    private record Textured(PartShape shape, String texture, Maps maps, boolean glows, boolean glass) {}

    private record Maps(String normal, String roughness, String metallic, String occlusion, String emission) {

        static final Maps NONE = new Maps("", "", "", "", "");

        static Maps of(Part part, boolean glows) {
            boolean pbr = part.shading == PartShading.PBR;
            String emission = glows ? part.emissionMap : "";
            if (!pbr) return emission.isEmpty() ? NONE : new Maps("", "", "", "", emission);
            Maps maps = new Maps(part.normalMap, part.roughnessMap, part.metallicMap, part.occlusionMap, emission);
            return maps.equals(NONE) ? NONE : maps;
        }

        float[] bound() {
            return new float[] {on(normal), on(roughness), on(metallic), on(occlusion)};
        }

        private static float on(String map) {
            return map.isEmpty() ? 0 : 1;
        }
    }

    private static final int[] STILL_COUNTS = new int[PartShape.values().length];
    private static final int[] MOVING_COUNTS = new int[PartShape.values().length];

    public static int stillCount() {
        return total(STILL_COUNTS);
    }

    public static int movingCount() {
        return total(MOVING_COUNTS);
    }

    private static int total(int[] counts) {
        int sum = 0;
        for (int count : counts) sum += count;
        return sum;
    }

    private static Identifier[] ids(String base) {
        PartShape[] shapes = PartShape.values();
        Identifier[] out = new Identifier[shapes.length];
        for (int at = 0; at < shapes.length; at++) {
            String path = shapes[at] == PartShape.BLOCK ? base : base + "_" + shapes[at].name().toLowerCase(Locale.ROOT);
            out[at] = Identifier.fromNamespaceAndPath("moud", path);
        }
        return out;
    }

    private Parts() {}

    // textured parts are gathered once a frame, so each texture's mesh only drains its own list
    public static void prepareFrame() {
        for (List<Part> packed : PACKED.values()) packed.clear();
        physical = false;
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        for (Part part : tree.ofClass(Classes.PART)) {
            if (part.shading == PartShading.PBR) physical = true;
            if (plain(part) || part instanceof MeshPart || Skins.wearsSkin(part) || SpecialMeshes.drawn(part)) continue;
            if (ViewportFrame.inside(part)) continue;
            boolean ghost = EditorView.ghost(part);
            if (!ghost && (!part.visible || part.transparency >= 1.0)) continue;
            boolean glows = glows(part);
            Textured key = new Textured(part.shape, part.texture, Maps.of(part, glows), glows, isTransparent(part));
            PACKED.computeIfAbsent(key, made -> {
                registerTextured(made);
                return new ArrayList<>();
            }).add(part);
        }
    }

    private static boolean plain(Part part) {
        if (!part.texture.isEmpty() || glows(part)) return false;
        return part.shading != PartShading.PBR || part.normalMap.isEmpty() && part.roughnessMap.isEmpty()
                && part.metallicMap.isEmpty() && part.occlusionMap.isEmpty();
    }

    private static boolean glows(Part part) {
        Color glow = part.emission;
        return part.emissionStrength > 0 && (glow.r() > 0 || glow.g() > 0 || glow.b() > 0);
    }

    private static void registerTextured(Textured key) {
        String name = "parts_tex_" + key.shape().name().toLowerCase(Locale.ROOT)
                + (key.glass() ? "_glass_" : "_") + Integer.toHexString(key.texture().hashCode())
                + (key.maps() == Maps.NONE && !key.glows() ? "" : "_" + Integer.toHexString(Objects.hash(key.maps(), key.glows())));
        InstancedMesh.Builder<Lit> builder = key.glass() ? glass(key.shape(), key.glows()) : mesh(key.shape(), key.glows());
        Maps maps = key.maps();
        float[] bound = maps.bound();
        float[] glow = {key.glows() ? 1 : 0, maps.emission().isEmpty() ? 0 : 1};
        builder.shader(Identifier.fromNamespaceAndPath("moud", "instance/part_textured"))
                .extraSampler("Albedo", () -> albedo(key.texture()), 2)
                .uniform("Maps", () -> bound)
                .uniform("Glow", () -> glow);
        if (!maps.normal().isEmpty()) builder.extraSampler("NormalMap", () -> map(maps.normal(), true), 5);
        if (!maps.roughness().isEmpty()) builder.extraSampler("RoughnessMap", () -> map(maps.roughness(), false), 6);
        if (!maps.metallic().isEmpty()) builder.extraSampler("MetallicMap", () -> map(maps.metallic(), false), 7);
        if (!maps.occlusion().isEmpty()) builder.extraSampler("OcclusionMap", () -> map(maps.occlusion(), false), 8);
        if (!maps.emission().isEmpty()) builder.extraSampler("EmissionMap", () -> map(maps.emission(), false), 9);
        if (key.glows() && !key.glass()) builder.glowsInGBuffer();
        builder.onRender((ctx, batch) -> renderTextured(key, ctx, batch))
                .register(Identifier.fromNamespaceAndPath("moud", name));
    }

    private static int albedo(String texture) {
        return sharp(texture.isEmpty() ? EffectTextures.map("", false) : EffectTextures.of(texture));
    }

    private static int map(String src, boolean normal) {
        return sharp(EffectTextures.map(src, normal));
    }

    private static int sharp(EffectTextures.Sprite sprite) {
        if (sprite.nearest() && SHARP.add(sprite.gl())) Textures.filterNearest(sprite.gl());
        return sprite.gl();
    }

    private static void renderTextured(Textured key, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        List<Part> parts = PACKED.get(key);
        if (parts == null || parts.isEmpty()) return;
        Motion motion = ClientScene.motion();
        for (Part part : parts) emit(ctx, batch, motion, part, true);
    }

    public static void register() {
        for (PartShape shape : PartShape.values()) {
            int at = shape.ordinal();
            mesh(shape, false).staticInstances().onRender((ctx, batch) -> renderStatic(shape, ctx, batch)).register(STILL[at]);
            mesh(shape, false).onRender((ctx, batch) -> renderMoving(shape, ctx, batch)).register(MOVING[at]);
            glass(shape, false).onRender((ctx, batch) -> renderTransparent(shape, ctx, batch)).register(GLASS[at]);
        }
    }

    private static final InstanceLayout LAYOUT =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).float1(7).float1(8).vec3(9).vec3(10).build();
    private static final InstanceLayout GLOWING =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).float1(7).float1(8).vec3(9).vec3(10).vec4(11).build();

    private record Lit(Matrix4f transform, Vector4f color, Vector2f light, float casts, float tile,
            Vector3f surface, Vector3f offset, Vector4f glow) {}

    private static void write(Lit inst, InstancePacker p) {
        p.putMat4(inst.transform()).putVec4(inst.color())
                .putVec2(inst.light().x, inst.light().y)
                .putFloat(inst.casts()).putFloat(inst.tile())
                .putVec3(inst.surface())
                .putVec3(inst.offset());
    }

    private static InstancedMesh.Builder<Lit> builder(boolean glows) {
        if (!glows) return InstancedMesh.builder(LAYOUT, Parts::write);
        return InstancedMesh.builder(GLOWING, (inst, p) -> {
            write(inst, p);
            p.putVec4(inst.glow());
        });
    }

    private static final Vector3f SURFACE = new Vector3f();
    private static final Vector3f OFFSET = new Vector3f();
    private static final Vector4f GLOW = new Vector4f();
    private static final float[] SUN = new float[4];
    private static final float[] TINT_OF_SUN = new float[4];
    private static final float[] SKY = new float[4];
    private static boolean physical;

    public static boolean anyPhysical() {
        return physical;
    }

    public static int lightMap() {
        GpuTextureView view = Minecraft.getInstance().gameRenderer.levelLightmap();
        return view != null && view.texture() instanceof GlTexture texture ? texture.glId() : 0;
    }

    public static void invalidateStatic() {
        for (Identifier id : STILL) InstancedMesh.invalidate(id);
    }

    private static InstancedMesh.Builder<Lit> glass(PartShape shape, boolean glows) {
        return builder(glows)
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/part"))
                .extraSampler("LightMap", Parts::lightMap, 1)
                .extraSampler("BlockLight", BlockLightGrid::glId, 3)
                .uniform("LightVolume", BlockLightGrid::placed)
                .uniform("Shape", () -> new float[] {shapeCode(shape)})
                .uniform("SunLight", Parts::sun)
                .uniform("SunTint", Parts::sunTint)
                .uniform("Environment", Parts::sky)
                .extraCubeSampler("EnvCube", Ibl::cube, 4)
                .geometry(ShapeMeshes.of(shape))
                .renderState(RenderState.builder()
                        .blend(RenderState.BlendMode.ALPHA)
                        .depthWrite(false)
                        .backfaceCulling(false)
                        .build())
                .phase(InstancePhase.WORLD_TRANSLUCENT)
                .writeGBuffer(false)
                .worldSpace();
    }

    private static InstancedMesh.Builder<Lit> mesh(PartShape shape, boolean glows) {
        return builder(glows)
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/part"))
                .extraSampler("LightMap", Parts::lightMap, 1)
                .extraSampler("BlockLight", BlockLightGrid::glId, 3)
                .uniform("LightVolume", BlockLightGrid::placed)
                .uniform("Shape", () -> new float[] {shapeCode(shape)})
                .uniform("SunLight", Parts::sun)
                .uniform("SunTint", Parts::sunTint)
                .uniform("Environment", Parts::sky)
                .extraCubeSampler("EnvCube", Ibl::cube, 4)
                .geometry(ShapeMeshes.of(shape))
                .renderState(RenderState.DEFAULT)
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .worldSpace()
                .castsShadowPerInstance();
    }

    private static float shapeCode(PartShape shape) {
        return switch (shape) {
            case BALL -> 1;
            case CYLINDER -> 2;
            default -> 0;
        };
    }

    private static float[] sun() {
        ModelLighting lighting = ModelLighting.INSTANCE;
        SUN[0] = lighting.sunX();
        SUN[1] = lighting.sunY();
        SUN[2] = lighting.sunZ();
        SUN[3] = lighting.sunIntensity();
        return SUN;
    }

    private static float[] sunTint() {
        ModelLighting lighting = ModelLighting.INSTANCE;
        TINT_OF_SUN[0] = lighting.sunR();
        TINT_OF_SUN[1] = lighting.sunG();
        TINT_OF_SUN[2] = lighting.sunB();
        TINT_OF_SUN[3] = lighting.exposure();
        return TINT_OF_SUN;
    }

    private static float[] sky() {
        ModelLighting lighting = ModelLighting.INSTANCE;
        SKY[0] = lighting.ambientStrength();
        SKY[1] = lighting.envIntensity();
        SKY[2] = Ibl.maxLod();
        SKY[3] = Ibl.ready() ? 1 : 0;
        return SKY;
    }

    private static void renderStatic(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Motion motion = ClientScene.motion();
        List<Part> parts = tree.ofClass(Classes.PART);
        int emitted = 0;
        for (int n = 0; n < parts.size(); n++) {
            Part part = parts.get(n);
            if (!motion.isMoving(part) && write(shape, ctx, batch, motion, part, false, false)) emitted++;
        }
        STILL_COUNTS[shape.ordinal()] = emitted;
    }

    private static final List<Part> SEE_THROUGH = new ArrayList<>();

    private static void renderTransparent(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Motion motion = ClientScene.motion();
        var eye = ctx.cameraPos();
        SEE_THROUGH.clear();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (part.shape == shape && isTransparent(part) && !ViewportFrame.inside(part)) SEE_THROUGH.add(part);
        }
        SEE_THROUGH.sort(Comparator.comparingDouble((Part part) -> {
            Vector3 at = motion.sample(part, ctx.deltaTick()).position();
            double dx = at.x() - eye.x, dy = at.y() - eye.y, dz = at.z() - eye.z;
            return dx * dx + dy * dy + dz * dz;
        }).reversed());
        for (Part part : SEE_THROUGH) write(shape, ctx, batch, motion, part, true, true);
    }

    private static boolean isTransparent(Part part) {
        return part.transparency > 0.001 && part.transparency < 1.0 || EditorView.ghost(part);
    }

    private static void renderMoving(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        Motion motion = ClientScene.motion();
        int emitted = 0;
        for (Instance instance : motion.moving()) {
            if (instance instanceof Part part && write(shape, ctx, batch, motion, part, true, false)) emitted++;
        }
        MOVING_COUNTS[shape.ordinal()] = emitted;
    }

    private static boolean write(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch,
            Motion motion, Part part, boolean cull, boolean glass) {
        if (part.shape != shape) return false;
        boolean ghost = EditorView.ghost(part);
        if (!ghost && (!part.visible || part.transparency >= 1.0)) return false;
        if (isTransparent(part) != glass) return false;
        if (part instanceof MeshPart) return false;
        if (Skins.wearsSkin(part)) return false;
        if (SpecialMeshes.drawn(part)) return false;
        if (!plain(part)) return false;
        if (ViewportFrame.inside(part)) return false;

        emit(ctx, batch, motion, part, cull);
        return true;
    }

    private static void emit(InstanceRenderContext ctx, InstanceBatch<Lit> batch, Motion motion, Part part, boolean cull) {
        boolean ghost = EditorView.ghost(part);
        CFrame world = motion.sample(part, ctx.deltaTick());
        Vector3 pos = world.position();
        Quat rot = world.rotation();
        Vector3 size = ShapeMeshes.scale(part);

        ROTATION.set((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w());
        MATRIX.translation((float) pos.x(), (float) pos.y(), (float) pos.z())
                .rotate(ROTATION)
                .scale((float) size.x(), (float) size.y(), (float) size.z());

        Color c = part.color;
        if (ghost) TINT.set(0.55f + c.r() * 0.3f, 0.75f + c.g() * 0.2f, 1.0f, EditorView.GHOST_ALPHA);
        else TINT.set(c.r(), c.g(), c.b(), (float) (1.0 - part.transparency));

        float radius = (float) (size.length() * 0.5);
        SURFACE.set(part.shading == PartShading.PBR ? 1 : 0, (float) part.roughness, (float) part.metallic);
        Vector3 offset = part.textureOffset;
        OFFSET.set((float) offset.x(), (float) offset.y(), (float) offset.z());
        Color glow = part.emission;
        GLOW.set(glow.r(), glow.g(), glow.b(), (float) part.emissionStrength);
        Lit instance = new Lit(MATRIX, TINT, PartLight.of(part, pos), part.castShadow ? 1 : 0,
                (float) part.studsPerTile, SURFACE, OFFSET, GLOW);
        if (cull) {
            batch.addVisible(instance, pos.x(), pos.y(), pos.z(), radius);
        } else {
            batch.add(instance, pos.x(), pos.y(), pos.z(), radius);
        }
    }
}
