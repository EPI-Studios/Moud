package com.moud.client.fabric.model.loader;

import com.moud.client.fabric.model.AnimationClip;
import com.moud.client.fabric.model.BoneNode;
import com.moud.client.fabric.model.BoneTrack;
import com.moud.client.fabric.model.CubeGeometry;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.render.MoudTextures;
import fr.mrqsdf.bbmodelreader.BbModelReader;
import fr.mrqsdf.bbmodelreader.data.BbModel;
import fr.mrqsdf.bbmodelreader.data.Element;
import fr.mrqsdf.bbmodelreader.data.Face;
import fr.mrqsdf.bbmodelreader.data.Outliner;
import fr.mrqsdf.bbmodelreader.data.OutlinerChild;
import fr.mrqsdf.bbmodelreader.data.OutlinerElementRef;
import fr.mrqsdf.bbmodelreader.data.OutlinerGroupRef;
import fr.mrqsdf.bbmodelreader.data.Texture;
import fr.mrqsdf.bbmodelreader.data.animation.Animation;
import fr.mrqsdf.bbmodelreader.data.animation.Animator;
import fr.mrqsdf.bbmodelreader.data.animation.DataPoint;
import fr.mrqsdf.bbmodelreader.data.animation.Keyframe;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BbmodelLoader {
    private BbmodelLoader() {}

    public static ModelAsset load(byte[] jsonBytes, String modelName) {
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        String cacheKey = "moud_" + Long.toHexString(System.nanoTime());
        BbModel bb = BbModelReader.loadFromJson(json, cacheKey);
        return fromBbModel(bb, modelName);
    }

    private static ModelAsset fromBbModel(BbModel bb, String modelName) {
        int resW = 64, resH = 64;
        if (bb.getResolution() != null) {
            resW = Math.max(1, bb.getResolution().getWidth());
            resH = Math.max(1, bb.getResolution().getHeight());
        }

        String safeName = modelName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        Texture[] textures = bb.getTextures();
        List<Identifier> texIds = new ArrayList<>();
        if (textures != null) {
            for (int i = 0; i < textures.length; i++) {
                Identifier id = Identifier.of("moud", "bbmodel/" + safeName + "/tex" + i);
                Texture tex = textures[i];
                if (tex != null) {
                    byte[] png = extractPng(tex);
                    if (png != null) MoudTextures.registerRaw(id, png);
                }
                texIds.add(id);
            }
        }

        Map<String, Element> elementsByUuid = new HashMap<>();
        Element[] elements = bb.getElements();
        if (elements != null) {
            for (Element e : elements) {
                if (e != null && e.getUuid() != null) elementsByUuid.put(e.getUuid(), e);
            }
        }

        List<BoneNode> rootBones = new ArrayList<>();
        Map<String, BoneNode> bonesByUuid = new HashMap<>();
        Outliner[] outliner = bb.getOutliner();
        if (outliner != null) {
            for (Outliner root : outliner) {
                if (root == null) continue;
                BoneNode bone = buildBone(root, elementsByUuid, resW, resH);
                rootBones.add(bone);
                collectBones(bone, bonesByUuid);
            }
        }

        Map<String, AnimationClip> animations = new HashMap<>();
        Animation[] anims = bb.getAnimations();
        if (anims != null) {
            for (Animation anim : anims) {
                if (anim == null || anim.getName() == null) continue;
                animations.put(anim.getName(), buildClip(anim));
            }
        }

        return new ModelAsset(modelName, rootBones, bonesByUuid, animations, texIds, resW, resH);
    }

    private static void collectBones(BoneNode bone, Map<String, BoneNode> out) {
        if (!bone.uuid().isEmpty()) out.put(bone.uuid(), bone);
        for (BoneNode child : bone.children()) collectBones(child, out);
    }

    private static BoneNode buildBone(Outliner outliner, Map<String, Element> elements, int resW, int resH) {
        float px = 0, py = 0, pz = 0;
        float[] origin = outliner.getOrigin();
        if (origin != null && origin.length >= 3) { px = origin[0]; py = origin[1]; pz = origin[2]; }

        List<CubeGeometry> cubes = new ArrayList<>();
        List<BoneNode> children = new ArrayList<>();

        if (outliner.getChildren() != null) {
            for (OutlinerChild child : outliner.getChildren()) {
                if (child instanceof OutlinerElementRef eRef) {
                    Element e = elements.get(eRef.getUuid());
                    if (e != null) {
                        CubeGeometry cube = toCube(e);
                        if (cube != null) cubes.add(cube);
                    }
                } else if (child instanceof OutlinerGroupRef gRef) {
                    children.add(buildBone(gRef.getGroup(), elements, resW, resH));
                }
            }
        }

        String uuid = outliner.getUuid() != null ? outliner.getUuid() : "";
        String name = outliner.getName() != null ? outliner.getName() : "";
        return new BoneNode(name, uuid, px, py, pz, cubes, children);
    }

    private static CubeGeometry toCube(Element e) {
        float[] from = e.getFrom(), to = e.getTo();
        if (from == null || to == null || from.length < 3 || to.length < 3) return null;
        Face f = e.getFaces();
        return new CubeGeometry(
                from[0], from[1], from[2], to[0], to[1], to[2],
                toFaceUv(f != null ? f.getNorth() : null),
                toFaceUv(f != null ? f.getSouth() : null),
                toFaceUv(f != null ? f.getEast()  : null),
                toFaceUv(f != null ? f.getWest()  : null),
                toFaceUv(f != null ? f.getUp()    : null),
                toFaceUv(f != null ? f.getDown()  : null)
        );
    }

    private static CubeGeometry.FaceUV toFaceUv(Face.FaceValue fv) {
        if (fv == null) return null;
        float[] uv = fv.getUv();
        if (uv == null || uv.length < 4) return null;
        int tex = fv.getTexture() != null ? fv.getTexture() : 0;
        int rot = fv.getRotation() != null ? fv.getRotation() : 0;
        return new CubeGeometry.FaceUV(uv[0], uv[1], uv[2], uv[3], rot, tex);
    }

    private static AnimationClip buildClip(Animation anim) {
        float duration = anim.getLength() != null ? anim.getLength() : 0f;
        String loop    = anim.getLoop()   != null ? anim.getLoop()   : "once";

        Map<String, BoneTrack> tracks = new HashMap<>();
        Map<String, Animator> animators = anim.getAnimators();
        if (animators != null) {
            for (Map.Entry<String, Animator> entry : animators.entrySet()) {
                Animator animator = entry.getValue();
                if (animator == null || animator.getKeyframes() == null) continue;
                tracks.put(entry.getKey(), buildTrack(animator.getKeyframes()));
            }
        }
        return new AnimationClip(anim.getName(), duration, loop, tracks);
    }

    private static BoneTrack buildTrack(Keyframe[] keyframes) {
        List<float[]> pos = new ArrayList<>(), rot = new ArrayList<>(), scale = new ArrayList<>();
        for (Keyframe kf : keyframes) {
            if (kf == null || kf.getTime() == null || kf.getDataPoints() == null || kf.getDataPoints().isEmpty()) continue;
            DataPoint dp = kf.getDataPoints().get(0);
            if (dp == null) continue;
            float t = kf.getTime();
            float x = parseFloat(dp.getX(), 0f), y = parseFloat(dp.getY(), 0f), z = parseFloat(dp.getZ(), 0f);
            switch (kf.getChannel() != null ? kf.getChannel() : "") {
                case "position" -> pos.add(new float[]{t, x, y, z});
                case "rotation" -> rot.add(new float[]{t, x, y, z});
                case "scale"    -> scale.add(new float[]{t, x, y, z});
            }
        }
        pos.sort(Comparator.comparingDouble(a -> a[0]));
        rot.sort(Comparator.comparingDouble(a -> a[0]));
        scale.sort(Comparator.comparingDouble(a -> a[0]));
        return new BoneTrack(
                times(pos), vals(pos, 1), vals(pos, 2), vals(pos, 3),
                times(rot), vals(rot, 1), vals(rot, 2), vals(rot, 3),
                times(scale), vals(scale, 1), vals(scale, 2), vals(scale, 3)
        );
    }

    private static float[] times(List<float[]> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i)[0];
        return a;
    }

    private static float[] vals(List<float[]> list, int comp) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i)[comp];
        return a;
    }

    private static float parseFloat(String s, float def) {
        if (s == null || s.isBlank()) return def;
        try { return Float.parseFloat(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static byte[] extractPng(Texture tex) {
        String source = tex.getSource();
        if (source == null || !source.contains(",")) return null;
        try {
            return Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
