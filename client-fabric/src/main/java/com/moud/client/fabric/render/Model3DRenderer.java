package com.moud.client.fabric.render;

import com.moud.client.fabric.model.AnimationClip;
import com.moud.client.fabric.model.BoneNode;
import com.moud.client.fabric.model.BoneTrack;
import com.moud.client.fabric.model.CubeGeometry;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.model.ModelInstance;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Model3DRenderer {
    private static final float INV16 = 1f / 16f;
    private static final Map<Long, ModelInstance> instances = new HashMap<>();

    private Model3DRenderer() {}

    public static void render(VertexConsumerProvider.Immediate consumers,
                              MatrixStack matrices,
                              SceneSnapshot.NodeSnapshot node,
                              int light) {
        String modelPath = stringProp(node, "model_path");
        if (modelPath == null || modelPath.isBlank()) return;

        ModelAsset asset = ModelCache.get(modelPath);
        if (asset == null) return;

        ModelInstance inst = instances.computeIfAbsent(node.nodeId(), id -> new ModelInstance());
        inst.update(
                stringProp(node, "animation"),
                stringProp(node, "animation_loop"),
                parseFloat(stringProp(node, "animation_speed"), 1f)
        );

        AnimationClip clip = asset.animations().get(inst.currentAnim());
        float animTime = inst.currentTime(clip);

        matrices.push();
        matrices.scale(INV16, INV16, INV16);
        for (BoneNode root : asset.rootBones()) {
            renderBone(consumers, matrices, root, clip, animTime, asset, light);
        }
        matrices.pop();
    }

    private static void renderBone(VertexConsumerProvider.Immediate consumers,
                                   MatrixStack matrices,
                                   BoneNode bone,
                                   AnimationClip clip,
                                   float animTime,
                                   ModelAsset asset,
                                   int light) {
        BoneTrack track = clip != null ? clip.tracks().get(bone.uuid()) : null;
        float px = bone.pivotX(), py = bone.pivotY(), pz = bone.pivotZ();

        float aPosX = 0, aPosY = 0, aPosZ = 0;
        float aRotX = 0, aRotY = 0, aRotZ = 0;
        float aScaleX = 1, aScaleY = 1, aScaleZ = 1;
        if (track != null) {
            aPosX  = track.posX(animTime);  aPosY  = track.posY(animTime);  aPosZ  = track.posZ(animTime);
            aRotX  = track.rotX(animTime);  aRotY  = track.rotY(animTime);  aRotZ  = track.rotZ(animTime);
            aScaleX = track.scaleX(animTime); aScaleY = track.scaleY(animTime); aScaleZ = track.scaleZ(animTime);
        }

        matrices.push();
        // position delta moves the bone origin, then we rotate/scale around the (moved) pivot
        matrices.translate(aPosX, aPosY, aPosZ);
        matrices.translate(px, py, pz);
        if (aRotX != 0 || aRotY != 0 || aRotZ != 0) {
            // XYZ intrinsic Euler (blockbench convention) → rotateX then rotateY then rotateZ
            matrices.multiply(new Quaternionf()
                    .rotateX((float) Math.toRadians(aRotX))
                    .rotateY((float) Math.toRadians(aRotY))
                    .rotateZ((float) Math.toRadians(aRotZ)));
        }
        if (aScaleX != 1 || aScaleY != 1 || aScaleZ != 1) {
            matrices.scale(aScaleX, aScaleY, aScaleZ);
        }
        matrices.translate(-px, -py, -pz);

        for (CubeGeometry cube : bone.cubes()) {
            renderCube(consumers, matrices, cube, asset, light);
        }
        for (BoneNode child : bone.children()) {
            renderBone(consumers, matrices, child, clip, animTime, asset, light);
        }
        matrices.pop();
    }

    private static void renderCube(VertexConsumerProvider.Immediate consumers,
                                   MatrixStack matrices,
                                   CubeGeometry c,
                                   ModelAsset asset,
                                   int light) {
        float x0 = c.fromX(), y0 = c.fromY(), z0 = c.fromZ();
        float x1 = c.toX(),   y1 = c.toY(),   z1 = c.toZ();
        // north (-Z): TL→TR→BR→BL viewed from -Z
        face(consumers, matrices, c.north(),  asset, light, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0, 0,0,-1);
        // south (+Z)
        face(consumers, matrices, c.south(),  asset, light, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1, 0,0,1);
        // east (+X)
        face(consumers, matrices, c.east(),   asset, light, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1, 1,0,0);
        // west (-X)
        face(consumers, matrices, c.west(),   asset, light, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0, -1,0,0);
        // up (+Y)
        face(consumers, matrices, c.up(),     asset, light, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, 0,1,0);
        // down (-Y)
        face(consumers, matrices, c.down(),   asset, light, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0);
    }

    private static void face(VertexConsumerProvider.Immediate consumers,
                              MatrixStack matrices,
                              CubeGeometry.FaceUV face,
                              ModelAsset asset,
                              int light,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float nx, float ny, float nz) {
        if (face == null) return;
        int idx = face.textureIndex();
        List<Identifier> ids = asset.textureIds();
        Identifier texId = (idx >= 0 && idx < ids.size()) ? ids.get(idx) : MoudTextures.WHITE_ID;
        if ("moud".equals(texId.getNamespace())
                && texId.getPath().startsWith("bbmodel/")
                && !MoudTextures.isRawReady(texId)) {
            texId = MoudTextures.WHITE_ID;
        }

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texId));
        MatrixStack.Entry entry = matrices.peek();

        float rW = asset.resWidth(), rH = asset.resHeight();
        float[] uv = uvRotated(face.u1() / rW, face.v1() / rH, face.u2() / rW, face.v2() / rH, face.rotation());
        int overlay = OverlayTexture.DEFAULT_UV;

        vertex(vc, entry, x0, y0, z0, uv[0], uv[1], light, overlay, nx, ny, nz);
        vertex(vc, entry, x1, y1, z1, uv[2], uv[3], light, overlay, nx, ny, nz);
        vertex(vc, entry, x2, y2, z2, uv[4], uv[5], light, overlay, nx, ny, nz);
        vertex(vc, entry, x3, y3, z3, uv[6], uv[7], light, overlay, nx, ny, nz);
    }

    // Returns [TL_u, TL_v, TR_u, TR_v, BR_u, BR_v, BL_u, BL_v] after applying rotation
    private static float[] uvRotated(float u1, float v1, float u2, float v2, int rotation) {
        return switch (rotation) {
            case 90  -> new float[]{ u1, v2, u1, v1, u2, v1, u2, v2 };
            case 180 -> new float[]{ u2, v2, u1, v2, u1, v1, u2, v1 };
            case 270 -> new float[]{ u2, v1, u2, v2, u1, v2, u1, v1 };
            default  -> new float[]{ u1, v1, u2, v1, u2, v2, u1, v2 };
        };
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry,
                                float x, float y, float z, float u, float v,
                                int light, int overlay, float nx, float ny, float nz) {
        vc.vertex(entry, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    public static void evict(long nodeId)  { instances.remove(nodeId); }
    public static void clearAll()          { instances.clear(); }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null) return null;
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return null;
        for (SceneSnapshot.Property p : props) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }

    private static float parseFloat(String s, float def) {
        if (s == null) return def;
        try { float v = Float.parseFloat(s.trim()); return Float.isFinite(v) ? v : def; }
        catch (Exception e) { return def; }
    }
}
