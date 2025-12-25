package com.moud.client.editor.picking;

import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.selection.SceneSelectionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.WorldViewCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Map;

public final class SceneObjectPicker {
    private static final double MAX_REACH = 100.0;

    private SceneObjectPicker() {
    }

    @Nullable
    public static SceneObject pickHoveredSceneObject() {
        if (!SceneSessionManager.getInstance().isEditorActive()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getCameraEntity() == null) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        Ray ray = computeMouseRay(camera);
        Vec3d rayStart;
        Vec3d rayDirection;
        if (ray != null) {
            rayStart = ray.origin();
            rayDirection = ray.direction();
        } else {
            rayStart = camera.getPos();
            Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            if (forward.lengthSquared() < 1.0E-6) {
                return null;
            }
            rayDirection = forward.normalize();
        }

        Vec3d rayEnd = rayStart.add(rayDirection.multiply(MAX_REACH));
        SceneObject best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (SceneObject object : SceneSessionManager.getInstance().getSceneGraph().getObjects()) {
            if (object == null) {
                continue;
            }
            String type = object.getType() == null ? "" : object.getType().toLowerCase(Locale.ROOT);
            if (!isPickable(type)) {
                continue;
            }
            Box box = boundsForObject(object, type);
            if (box == null) {
                continue;
            }
            Vec3d hit = raycastBox(rayStart, rayEnd, box);
            if (hit == null) {
                continue;
            }
            double distSq = rayStart.squaredDistanceTo(hit);
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                best = object;
            }
        }

        return best;
    }

    private static boolean isPickable(String type) {
        return switch (type) {
            case "terrain", "post_effect", "group" -> false;
            default -> true;
        };
    }

    @Nullable
    private static Box boundsForObject(SceneObject object, String type) {
        if ("zone".equals(type)) {
            return extractZoneBox(object);
        }

        Map<String, Object> props = object.getProperties();
        Vec3d position = readVec3(props.get("position"), null);
        if (position == null) {
            return null;
        }

        if ("model".equals(type)) {
            Box meshBounds = tryBoundModelMesh(object.getId(), position);
            if (meshBounds != null) {
                return meshBounds;
            }
        }

        Vec3d scale = readVec3(props.get("scale"), new Vec3d(1, 1, 1));
        Vec3d half = new Vec3d(
                Math.max(0.05, Math.abs(scale.x) * 0.5),
                Math.max(0.05, Math.abs(scale.y) * 0.5),
                Math.max(0.05, Math.abs(scale.z) * 0.5)
        );

        if ("light".equals(type)) {
            double radius = toDouble(props.get("radius"), 0.35);
            half = new Vec3d(Math.max(0.15, radius), Math.max(0.15, radius), Math.max(0.15, radius));
        } else if ("marker".equals(type)) {
            half = new Vec3d(0.2, 0.2, 0.2);
        } else if ("particle_emitter".equals(type)) {
            half = new Vec3d(0.35, 0.35, 0.35);
        } else if ("camera".equals(type)) {
            half = new Vec3d(0.35, 0.35, 0.35);
        }

        return new Box(position.subtract(half), position.add(half));
    }

    @Nullable
    private static Box tryBoundModelMesh(String objectId, Vec3d position) {
        if (objectId == null) {
            return null;
        }
        Long modelId = SceneSelectionManager.getInstance().getBindingForObject(objectId);
        if (modelId == null) {
            return null;
        }
        var model = com.moud.client.model.ClientModelManager.getInstance().getModel(modelId);
        if (model == null || !model.hasMeshBounds()) {
            return null;
        }
        Vec3d min = new Vec3d(model.getMeshMin().x, model.getMeshMin().y, model.getMeshMin().z);
        Vec3d max = new Vec3d(model.getMeshMax().x, model.getMeshMax().y, model.getMeshMax().z);
        Vec3d half = max.subtract(min).multiply(0.5);
        Vec3d center = min.add(half);
        Vec3d worldCenter = position.add(center.x, center.y, center.z);
        return new Box(
                worldCenter.x - half.x,
                worldCenter.y - half.y,
                worldCenter.z - half.z,
                worldCenter.x + half.x,
                worldCenter.y + half.y,
                worldCenter.z + half.z
        );
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Box extractZoneBox(SceneObject object) {
        Map<String, Object> props = object.getProperties();
        Object c1Raw = props.get("corner1");
        Object c2Raw = props.get("corner2");
        if (!(c1Raw instanceof Map<?, ?>) || !(c2Raw instanceof Map<?, ?>)) {
            return null;
        }
        double x1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("x"), 0);
        double y1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("y"), 0);
        double z1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("z"), 0);
        double x2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("x"), 0);
        double y2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("y"), 0);
        double z2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("z"), 0);
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        double maxZ = Math.max(z1, z2);
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    private static Vec3d readVec3(Object raw, @Nullable Vec3d fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback != null ? fallback.x : 0);
            double y = toDouble(map.get("y"), fallback != null ? fallback.y : 0);
            double z = toDouble(map.get("z"), fallback != null ? fallback.z : 0);
            return new Vec3d(x, y, z);
        }
        return fallback;
    }

    private static double toDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Nullable
    private static Vec3d raycastBox(Vec3d rayStart, Vec3d rayEnd, Box box) {
        Vec3d rayDir = rayEnd.subtract(rayStart).normalize();

        double tMin = 0.0;
        double tMax = rayStart.distanceTo(rayEnd);

        if (Math.abs(rayDir.x) < 0.0001) {
            if (rayStart.x < box.minX || rayStart.x > box.maxX) {
                return null;
            }
        } else {
            double t1 = (box.minX - rayStart.x) / rayDir.x;
            double t2 = (box.maxX - rayStart.x) / rayDir.x;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return null;
        }

        if (Math.abs(rayDir.y) < 0.0001) {
            if (rayStart.y < box.minY || rayStart.y > box.maxY) {
                return null;
            }
        } else {
            double t1 = (box.minY - rayStart.y) / rayDir.y;
            double t2 = (box.maxY - rayStart.y) / rayDir.y;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return null;
        }

        if (Math.abs(rayDir.z) < 0.0001) {
            if (rayStart.z < box.minZ || rayStart.z > box.maxZ) {
                return null;
            }
        } else {
            double t1 = (box.minZ - rayStart.z) / rayDir.z;
            double t2 = (box.maxZ - rayStart.z) / rayDir.z;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return null;
        }

        return rayStart.add(rayDir.multiply(tMin));
    }

    @Nullable
    private static Ray computeMouseRay(Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        Window window = client.getWindow();
        double windowWidth = window.getWidth();
        double windowHeight = window.getHeight();
        if (windowWidth <= 0 || windowHeight <= 0) {
            return null;
        }

        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();

        float ndcX = (float) ((mouseX / windowWidth) * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (mouseY / windowHeight) * 2.0);

        Matrix4f view = new Matrix4f();
        Matrix4f projection = new Matrix4f();
        if (WorldViewCapture.copyMatrices(view, projection)) {
            Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
            Vector4f near = new Vector4f(ndcX, ndcY, -1f, 1f);
            Vector4f far = new Vector4f(ndcX, ndcY, 1f, 1f);
            inverse.transform(near);
            inverse.transform(far);
            if (near.w != 0f) near.div(near.w);
            if (far.w != 0f) far.div(far.w);

            Vec3d start = new Vec3d(near.x, near.y, near.z);
            Vec3d end = new Vec3d(far.x, far.y, far.z);
            Vec3d dir = end.subtract(start);
            double lenSq = dir.lengthSquared();
            if (lenSq > 1.0E-6) {
                return new Ray(start, dir.normalize());
            }
        }

        return null;
    }

    private record Ray(Vec3d origin, Vec3d direction) {
    }
}
