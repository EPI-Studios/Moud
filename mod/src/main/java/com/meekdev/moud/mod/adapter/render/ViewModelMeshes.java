package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.asset.BbmodelImport;
import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.character.ViewModel;
import com.meekdev.moud.core.character.ViewModels;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class ViewModelMeshes {

    private static final double PX = 1.0 / 16.0;
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private record Quad(float[] corners, float[] uv, Vector3f normal) {}

    private record Piece(@Nullable String group, int texture, List<Quad> quads) {}

    private record Mesh(String text, List<Piece> pieces, List<Identifier> textures) {}

    private static final Map<String, Mesh> MESHES = new HashMap<>();

    private ViewModelMeshes() {}

    static void draw(ViewModel view, Map<String, Bone> joints, PoseStack poses, SubmitNodeCollector out, int light) {
        String text = ViewModels.modelText(view.model);
        BbmodelImport.Rig rig = ViewModels.model(view);
        if (text == null || rig == null) return;
        Mesh mesh = mesh(view.model, text, rig);
        if (mesh == null) return;
        Bone camera = ViewModels.camera(view);
        Map<String, Matrix4f> frames = new HashMap<>();
        for (Piece piece : mesh.pieces()) {
            String key = piece.group() == null ? "" : piece.group();
            if (frames.containsKey(key)) continue;
            Bone bone = piece.group() == null ? null : joints.get(piece.group());
            frames.put(key, bone != null ? matrix(ViewModels.inView(view, bone), bone.scale)
                    : matrix(camera == null ? CFrame.IDENTITY : camera.cframe.inverse(), Vector3.ONE));
        }
        for (int t = 0; t < mesh.textures().size(); t++) {
            int texture = t;
            List<Piece> pieces = new ArrayList<>();
            for (Piece piece : mesh.pieces()) {
                if (piece.texture() == texture) pieces.add(piece);
            }
            if (pieces.isEmpty()) continue;
            out.submitCustomGeometry(poses, RenderTypes.entityCutout(mesh.textures().get(texture)), (pose, consumer) -> {
                for (Piece piece : pieces) {
                    Matrix4f whole = new Matrix4f(pose.pose()).mul(frames.get(piece.group() == null ? "" : piece.group()));
                    Matrix4f turn = frames.get(piece.group() == null ? "" : piece.group());
                    for (Quad quad : piece.quads()) emit(consumer, pose, whole, turn, quad, light);
                }
            });
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f whole, Matrix4f turn, Quad quad, int light) {
        Vector3f normal = turn.transformDirection(new Vector3f(quad.normal())).normalize();
        for (int n = 0; n < 4; n++) {
            consumer.addVertex(whole, quad.corners()[n * 3], quad.corners()[n * 3 + 1], quad.corners()[n * 3 + 2])
                    .setColor(-1)
                    .setUv(quad.uv()[n * 2], quad.uv()[n * 2 + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal.x, normal.y, normal.z);
        }
    }

    private static Matrix4f matrix(CFrame frame, Vector3 scale) {
        Vector3 at = frame.position();
        Quat turn = frame.rotation();
        return new Matrix4f().translationRotateScale((float) at.x(), (float) at.y(), (float) at.z(),
                (float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w(),
                (float) scale.x(), (float) scale.y(), (float) scale.z());
    }

    private static @Nullable Mesh mesh(String res, String text, BbmodelImport.Rig rig) {
        Mesh known = MESHES.get(res);
        if (known != null && known.text().equals(text)) return known;
        if (known != null) release(known);
        try {
            Mesh made = build(res, text, rig);
            MESHES.put(res, made);
            return made;
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("view model {} cannot be drawn: {}", res, e.getMessage());
            MESHES.put(res, new Mesh(text, List.of(), List.of()));
            return null;
        }
    }

    private static void release(Mesh mesh) {
        for (Identifier id : mesh.textures()) Minecraft.getInstance().getTextureManager().release(id);
    }

    private static Mesh build(String res, String text, BbmodelImport.Rig rig) {
        if (!(Json.parse(text) instanceof Map<?, ?> root)) throw new IllegalArgumentException("a .bbmodel holds a JSON object");
        Map<String, String> owners = new HashMap<>();
        owners(list(root.get("outliner")), null, owners);
        Map<String, BbmodelImport.Group> groups = new HashMap<>();
        for (BbmodelImport.Group group : rig.groups()) groups.put(group.uuid(), group);

        double[] resolution = {16, 16};
        if (root.get("resolution") instanceof Map<?, ?> size) {
            resolution[0] = number(size.get("width"), 16);
            resolution[1] = number(size.get("height"), 16);
        }
        List<Identifier> textures = new ArrayList<>();
        List<double[]> sizes = new ArrayList<>();
        List<?> sheets = list(root.get("textures"));
        for (int n = 0; n < sheets.size(); n++) {
            Map<?, ?> sheet = sheets.get(n) instanceof Map<?, ?> m ? m : Map.of();
            textures.add(texture(res, n, sheet));
            sizes.add(new double[] {number(sheet.get("uv_width"), resolution[0]), number(sheet.get("uv_height"), resolution[1])});
        }

        List<Piece> pieces = new ArrayList<>();
        for (Object entry : list(root.get("elements"))) {
            if (!(entry instanceof Map<?, ?> element)) continue;
            String type = element.get("type") == null ? "cube" : String.valueOf(element.get("type"));
            if (!type.equals("cube") || Boolean.FALSE.equals(element.get("visibility"))) continue;
            BbmodelImport.Group group = groups.get(owners.get(String.valueOf(element.get("uuid"))));
            Vector3 origin = group == null ? Vector3.ZERO : group.origin();
            Map<Integer, List<Quad>> byTexture = new HashMap<>();
            cube(element, origin, sizes, byTexture);
            for (Map.Entry<Integer, List<Quad>> faces : byTexture.entrySet()) {
                pieces.add(new Piece(group == null ? null : group.name(), faces.getKey(), faces.getValue()));
            }
        }
        return new Mesh(text, pieces, textures);
    }

    private static void cube(Map<?, ?> element, Vector3 origin, List<double[]> sizes, Map<Integer, List<Quad>> into) {
        double grow = number(element.get("inflate"), 0);
        Vector3 from = vector(element.get("from")).sub(new Vector3(grow, grow, grow));
        Vector3 to = vector(element.get("to")).add(new Vector3(grow, grow, grow));
        Vector3 pivot = vector(element.get("origin"));
        Quat turn = Clip.euler(vector(element.get("rotation")), BbmodelImport.EULER);
        if (!(element.get("faces") instanceof Map<?, ?> faces)) return;
        for (String name : FACES) {
            if (!(faces.get(name) instanceof Map<?, ?> face) || !(face.get("texture") instanceof Number index)) continue;
            int texture = index.intValue();
            if (texture < 0 || texture >= sizes.size()) continue;
            List<?> uv = list(face.get("uv"));
            if (uv.size() < 4) continue;
            double[] size = sizes.get(texture);
            float u1 = (float) (number(uv.get(0), 0) / size[0]);
            float v1 = (float) (number(uv.get(1), 0) / size[1]);
            float u2 = (float) (number(uv.get(2), 0) / size[0]);
            float v2 = (float) (number(uv.get(3), 0) / size[1]);
            float[] corners = {u1, v1, u2, v1, u2, v2, u1, v2};
            int steps = ((int) Math.round(number(face.get("rotation"), 0) / 90) % 4 + 4) % 4;
            float[] mapped = new float[8];
            for (int n = 0; n < 4; n++) {
                int from4 = (n - steps + 4) % 4;
                mapped[n * 2] = corners[from4 * 2];
                mapped[n * 2 + 1] = corners[from4 * 2 + 1];
            }
            Vector3[] points = face(name, from, to);
            float[] placed = new float[12];
            for (int n = 0; n < 4; n++) {
                Vector3 p = pivot.add(turn.rotate(points[n].sub(pivot))).sub(origin).mul(PX);
                placed[n * 3] = (float) p.x();
                placed[n * 3 + 1] = (float) p.y();
                placed[n * 3 + 2] = (float) p.z();
            }
            Vector3 normal = turn.rotate(normal(name));
            into.computeIfAbsent(texture, key -> new ArrayList<>())
                    .add(new Quad(placed, mapped, new Vector3f((float) normal.x(), (float) normal.y(), (float) normal.z())));
        }
    }

    private static Vector3[] face(String name, Vector3 f, Vector3 t) {
        return switch (name) {
            case "north" -> new Vector3[] {v(t.x(), t.y(), f.z()), v(f.x(), t.y(), f.z()), v(f.x(), f.y(), f.z()), v(t.x(), f.y(), f.z())};
            case "south" -> new Vector3[] {v(f.x(), t.y(), t.z()), v(t.x(), t.y(), t.z()), v(t.x(), f.y(), t.z()), v(f.x(), f.y(), t.z())};
            case "east" -> new Vector3[] {v(t.x(), t.y(), t.z()), v(t.x(), t.y(), f.z()), v(t.x(), f.y(), f.z()), v(t.x(), f.y(), t.z())};
            case "west" -> new Vector3[] {v(f.x(), t.y(), f.z()), v(f.x(), t.y(), t.z()), v(f.x(), f.y(), t.z()), v(f.x(), f.y(), f.z())};
            case "up" -> new Vector3[] {v(f.x(), t.y(), f.z()), v(t.x(), t.y(), f.z()), v(t.x(), t.y(), t.z()), v(f.x(), t.y(), t.z())};
            default -> new Vector3[] {v(f.x(), f.y(), t.z()), v(t.x(), f.y(), t.z()), v(t.x(), f.y(), f.z()), v(f.x(), f.y(), f.z())};
        };
    }

    private static Vector3 normal(String name) {
        return switch (name) {
            case "north" -> new Vector3(0, 0, -1);
            case "south" -> new Vector3(0, 0, 1);
            case "east" -> new Vector3(1, 0, 0);
            case "west" -> new Vector3(-1, 0, 0);
            case "up" -> Vector3.UP;
            default -> new Vector3(0, -1, 0);
        };
    }

    private static Vector3 v(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    private static Identifier texture(String res, int index, Map<?, ?> sheet) {
        Identifier id = Identifier.fromNamespaceAndPath("moud", "view_model/" + Integer.toHexString(res.hashCode()) + "_" + index);
        String source = sheet.get("source") == null ? "" : String.valueOf(sheet.get("source"));
        int comma = source.indexOf(',');
        if (!source.startsWith("data:") || comma < 0) return id;
        try {
            NativeImage image = NativeImage.read(Base64.getDecoder().decode(source.substring(comma + 1)));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "moud view model " + res, image));
        } catch (IOException | IllegalArgumentException e) {
            MoudMod.LOG.warn("view model {} texture {} cannot be read: {}", res, index, e.getMessage());
        }
        return id;
    }

    private static void owners(List<?> entries, @Nullable String parent, Map<String, String> owners) {
        for (Object entry : entries) {
            if (entry instanceof String uuid) {
                if (parent != null) owners.put(uuid, parent);
            } else if (entry instanceof Map<?, ?> node && node.get("uuid") != null) {
                owners(list(node.get("children")), String.valueOf(node.get("uuid")), owners);
            }
        }
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static double number(Object value, double missing) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return missing;
            }
        }
        return missing;
    }

    private static Vector3 vector(Object value) {
        List<?> list = list(value);
        if (list.size() < 3) return Vector3.ZERO;
        return new Vector3(number(list.get(0), 0), number(list.get(1), 0), number(list.get(2), 0));
    }
}
