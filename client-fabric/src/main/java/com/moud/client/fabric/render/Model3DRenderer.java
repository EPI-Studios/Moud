package com.moud.client.fabric.render;

import com.moud.client.fabric.model.AnimationClip;
import com.moud.client.fabric.model.BoneNode;
import com.moud.client.fabric.model.BoneTrack;
import com.moud.client.fabric.model.CubeGeometry;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.model.ModelInstance;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Model3DRenderer {
    private static final float INV16 = 1f / 16f;
    private static final Map<Long, ModelInstance> instances = new HashMap<>();

    private Model3DRenderer() {}

    @FunctionalInterface
    public interface CubeTransformConsumer {
        void accept(Matrix4f modelMatrix);
    }

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
                ParseUtils.parseFloat(stringProp(node, "animation_speed"), 1f)
        );

        AnimationClip clip = asset.animations().get(inst.currentAnim());
        float animTime = inst.currentTime(clip);
        if (light <= 0) {
            light = 0x00F000F0;
        }

        matrices.push();
        matrices.scale(INV16, INV16, INV16);
        for (BoneNode root : asset.rootBones()) {
            renderBone(consumers, matrices, root, clip, animTime, asset, light);
        }
        matrices.pop();
    }

    public static boolean forEachCubeTransform(SceneSnapshot.NodeSnapshot node, CubeTransformConsumer consumer) {
        if (node == null || consumer == null) {
            return false;
        }
        String modelPath = stringProp(node, "model_path");
        if (modelPath == null || modelPath.isBlank()) {
            return false;
        }

        ModelAsset asset = ModelCache.get(modelPath);
        if (asset == null) {
            return false;
        }

        ModelInstance inst = instances.computeIfAbsent(node.nodeId(), id -> new ModelInstance());
        inst.update(
                stringProp(node, "animation"),
                stringProp(node, "animation_loop"),
                ParseUtils.parseFloat(stringProp(node, "animation_speed"), 1f)
        );

        AnimationClip clip = asset.animations().get(inst.currentAnim());
        float animTime = inst.currentTime(clip);
        Matrix4f root = new Matrix4f().scaling(INV16);
        for (BoneNode bone : asset.rootBones()) {
            visitBoneTransforms(bone, clip, animTime, root, consumer);
        }
        return true;
    }

    private static void visitBoneTransforms(BoneNode bone,
                                            AnimationClip clip,
                                            float animTime,
                                            Matrix4f parent,
                                            CubeTransformConsumer consumer) {
        BoneTrack track = clip != null ? clip.tracks().get(bone.uuid()) : null;
        float px = bone.pivotX(), py = bone.pivotY(), pz = bone.pivotZ();

        float posX = bone.posX(), posY = bone.posY(), posZ = bone.posZ();
        float rotX = bone.rotX(), rotY = bone.rotY(), rotZ = bone.rotZ();
        float scaleX = 1f, scaleY = 1f, scaleZ = 1f;
        if (track != null) {
            posX += track.posX(animTime);
            posY += track.posY(animTime);
            posZ += track.posZ(animTime);
            rotX += track.rotX(animTime);
            rotY += track.rotY(animTime);
            rotZ += track.rotZ(animTime);
            scaleX = track.scaleX(animTime);
            scaleY = track.scaleY(animTime);
            scaleZ = track.scaleZ(animTime);
        }

        Matrix4f boneMatrix = new Matrix4f(parent)
                .translate(posX, posY, posZ)
                .translate(px, py, pz);
        if (rotX != 0 || rotY != 0 || rotZ != 0) {
            boneMatrix.rotate(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(rotZ),
                    (float) Math.toRadians(rotY),
                    (float) Math.toRadians(rotX)
            ));
        }
        if (scaleX != 1f || scaleY != 1f || scaleZ != 1f) {
            boneMatrix.scale(scaleX, scaleY, scaleZ);
        }
        boneMatrix.translate(-px, -py, -pz);

        for (CubeGeometry cube : bone.cubes()) {
            consumer.accept(cubeTransform(boneMatrix, cube));
        }
        for (BoneNode child : bone.children()) {
            visitBoneTransforms(child, clip, animTime, boneMatrix, consumer);
        }
    }

    private static Matrix4f cubeTransform(Matrix4f boneMatrix, CubeGeometry cube) {
        float inflate = cube.inflate();
        float x0 = cube.fromX() - inflate;
        float y0 = cube.fromY() - inflate;
        float z0 = cube.fromZ() - inflate;
        float sx = (cube.toX() - cube.fromX()) + inflate * 2f;
        float sy = (cube.toY() - cube.fromY()) + inflate * 2f;
        float sz = (cube.toZ() - cube.fromZ()) + inflate * 2f;

        Matrix4f matrix = new Matrix4f(boneMatrix);
        if (cube.rotX() != 0 || cube.rotY() != 0 || cube.rotZ() != 0) {
            matrix.translate(cube.originX(), cube.originY(), cube.originZ());
            matrix.rotate(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(cube.rotZ()),
                    (float) Math.toRadians(cube.rotY()),
                    (float) Math.toRadians(cube.rotX())
            ));
            matrix.translate(-cube.originX(), -cube.originY(), -cube.originZ());
        }
        return matrix.translate(x0, y0, z0).scale(sx, sy, sz);
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

        float posX = bone.posX(), posY = bone.posY(), posZ = bone.posZ();
        float rotX = bone.rotX(), rotY = bone.rotY(), rotZ = bone.rotZ();
        float aScaleX = 1, aScaleY = 1, aScaleZ = 1;
        if (track != null) {
            posX += track.posX(animTime);
            posY += track.posY(animTime);
            posZ += track.posZ(animTime);
            rotX += track.rotX(animTime);
            rotY += track.rotY(animTime);
            rotZ += track.rotZ(animTime);
            aScaleX = track.scaleX(animTime); aScaleY = track.scaleY(animTime); aScaleZ = track.scaleZ(animTime);
        }

        matrices.push();
        matrices.translate(posX, posY, posZ);
        matrices.translate(px, py, pz);
        if (rotX != 0 || rotY != 0 || rotZ != 0) {
            matrices.multiply(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(rotZ),
                    (float) Math.toRadians(rotY),
                    (float) Math.toRadians(rotX)
            ));
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
        float inflate = c.inflate();
        float x0 = c.fromX() - inflate, y0 = c.fromY() - inflate, z0 = c.fromZ() - inflate;
        float x1 = c.toX() + inflate,   y1 = c.toY() + inflate,   z1 = c.toZ() + inflate;
        boolean rotated = c.rotX() != 0 || c.rotY() != 0 || c.rotZ() != 0;

        matrices.push();
        if (rotated) {
            matrices.translate(c.originX(), c.originY(), c.originZ());
            matrices.multiply(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(c.rotZ()),
                    (float) Math.toRadians(c.rotY()),
                    (float) Math.toRadians(c.rotX())
            ));
            matrices.translate(-c.originX(), -c.originY(), -c.originZ());
        }
        face(consumers, matrices, c.north(),  asset, light, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0, 0,0,-1);
        face(consumers, matrices, c.south(),  asset, light, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1, 0,0,1);
        face(consumers, matrices, c.east(),   asset, light, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1, 1,0,0);
        face(consumers, matrices, c.west(),   asset, light, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0, -1,0,0);
        face(consumers, matrices, c.up(),     asset, light, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, 0,1,0);
        face(consumers, matrices, c.down(),   asset, light, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0);
        matrices.pop();
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
        Identifier texId = (idx >= 0 && idx < ids.size()) ? ids.get(idx) : MoudTextures.white();
        if ("moud".equals(texId.getNamespace())
                && texId.getPath().startsWith("bbmodel/")
                && !MoudTextures.isRawReady(texId)) {
            texId = MoudTextures.white();
        }

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texId));
        MatrixStack.Entry entry = matrices.peek();

        float rW = asset.resWidth(), rH = asset.resHeight();
        float[] uv = uvRotated(face.u1() / rW, face.v1() / rH, face.u2() / rW, face.v2() / rH, face.rotation());
        int overlay = OverlayTexture.DEFAULT_UV;
        Vector3f normal = new Vector3f(nx, ny, nz);
        entry.getNormalMatrix().transform(normal).normalize();

        vertex(vc, entry, x0, y0, z0, uv[0], uv[1], light, overlay, normal.x, normal.y, normal.z);
        vertex(vc, entry, x1, y1, z1, uv[2], uv[3], light, overlay, normal.x, normal.y, normal.z);
        vertex(vc, entry, x2, y2, z2, uv[4], uv[5], light, overlay, normal.x, normal.y, normal.z);
        vertex(vc, entry, x3, y3, z3, uv[6], uv[7], light, overlay, normal.x, normal.y, normal.z);
    }

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

}
