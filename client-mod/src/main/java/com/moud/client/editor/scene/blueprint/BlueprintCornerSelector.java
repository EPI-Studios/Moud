package com.moud.client.editor.scene.blueprint;

import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.WorldViewCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BlueprintCornerSelector {

    public enum Corner { A, B }

    private static final BlueprintCornerSelector INSTANCE = new BlueprintCornerSelector();
    private static final double MAX_REACH = 100.0;
    private static final double EPSILON = 1.0E-6;

    private static final String TYPE_ZONE = "zone";
    private static final String TYPE_LIGHT = "light";
    private static final String TYPE_MARKER = "marker";
    private static final String TYPE_PARTICLE = "particle_emitter";
    private static final String TYPE_CAMERA = "camera";
    private static final String TYPE_POST_EFFECT = "post_effect";
    private static final String TYPE_GROUP = "group";


    private static final String PROP_POSITION = "position";
    private static final String PROP_SCALE = "scale";
    private static final String PROP_RADIUS = "radius";
    private static final String PROP_CORNER_1 = "corner1";
    private static final String PROP_CORNER_2 = "corner2";
    private static final String PROP_X = "x";
    private static final String PROP_Y = "y";
    private static final String PROP_Z = "z";

    private final AtomicBoolean selecting = new AtomicBoolean(false);

    @Nullable private Corner pendingCorner;
    @Nullable private Consumer<float[]> completionCallback;
    @Nullable private Vec3d previewPosition;

    private BlueprintCornerSelector() {}

    public static BlueprintCornerSelector getInstance() {
        return INSTANCE;
    }

    public boolean isPicking() {
        return selecting.get();
    }

    @Nullable
    public Corner getPendingCorner() {
        return pendingCorner;
    }

    @Nullable
    public Vec3d getPreviewPosition() {
        return previewPosition;
    }

    public void beginSelection(@NotNull Corner corner, @NotNull Consumer<float[]> callback) {
        this.pendingCorner = Objects.requireNonNull(corner, "Corner cannot be null");
        this.completionCallback = Objects.requireNonNull(callback, "Callback cannot be null");
        this.previewPosition = null;
        this.selecting.set(true);
        SceneEditorDiagnostics.log("Click to set corner " + corner + " (look at target)");
    }

    public void cancel() {
        this.pendingCorner = null;
        this.completionCallback = null;
        this.previewPosition = null;
        this.selecting.set(false);
    }

    public void updatePreview() {
        if (!selecting.get()) {
            previewPosition = null;
            return;
        }
        previewPosition = computeIntersection();
    }

    public boolean handleMouseButton(int button, int action) {
        if (!selecting.get() || pendingCorner == null || completionCallback == null) {
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            Vec3d result = computeIntersection();

            if (result != null) {
                float[] coords = { (float) result.x, (float) result.y, (float) result.z };
                completionCallback.accept(coords);
                SceneEditorDiagnostics.log("Corner %s set at %.2f, %.2f, %.2f".formatted(pendingCorner, result.x, result.y, result.z));
                cancel();
            } else {
                SceneEditorDiagnostics.log("No valid target for corner " + pendingCorner);
            }
            return true;
        }

        return false;
    }

    @Nullable
    private Vec3d computeIntersection() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.getCameraEntity() == null) {
            return null;
        }

        Camera camera = client.gameRenderer.getCamera();
        Ray ray = computeScreenRay(client, camera);
        if (ray == null) {
            Vec3d dir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
            ray = new Ray(camera.getPos(), dir);
        }

        Vec3d rayEnd = ray.origin.add(ray.direction.multiply(MAX_REACH));
        Intersection bestHit = new Intersection(Double.MAX_VALUE, null);

        checkBlockIntersection(client, ray.origin, rayEnd, bestHit);
        checkSceneObjectsIntersection(ray.origin, rayEnd, bestHit);
        checkRuntimeObjectIntersection(ray.origin, bestHit);

        return bestHit.position;
    }

    private void checkBlockIntersection(MinecraftClient client, Vec3d start, Vec3d end, Intersection bestHit) {
        if (client.world == null || client.getCameraEntity() == null) return;

        RaycastContext context = new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.getCameraEntity()
        );

        BlockHitResult result = client.world.raycast(context);
        if (result != null && result.getType() != HitResult.Type.MISS) {
            double distSq = start.squaredDistanceTo(result.getPos());
            if (distSq < bestHit.distSq) {
                bestHit.update(distSq, result.getPos());
            }
        }
    }

    private void checkSceneObjectsIntersection(Vec3d start, Vec3d end, Intersection bestHit) {
        var sceneGraph = SceneSessionManager.getInstance().getSceneGraph();
        if (sceneGraph == null) return;

        for (SceneObject object : sceneGraph.getObjects()) {
            if (object == null) continue;

            String type = object.getType() == null ? "" : object.getType().toLowerCase(Locale.ROOT);
            if (!isPickableType(type)) continue;

            Box box = calculateBounds(object, type);
            if (box == null) continue;

            Vec3d hit = intersectBox(start, end, box);
            if (hit != null) {
                double distSq = start.squaredDistanceTo(hit);
                if (distSq < bestHit.distSq) {
                    bestHit.update(distSq, hit);
                }
            }
        }
    }

    private void checkRuntimeObjectIntersection(Vec3d start, Intersection bestHit) {
        RaycastPicker picker = RaycastPicker.getInstance();
        picker.updateHover();

        RuntimeObject hovered = picker.getHoveredObject();
        if (hovered != null) {
            Vec3d pos = hovered.getPosition();
            if (pos != null) {
                double distSq = start.squaredDistanceTo(pos);
                if (distSq < bestHit.distSq) {
                    bestHit.update(distSq, pos);
                }
            }
        }
    }

    private boolean isPickableType(String type) {
        return !TYPE_POST_EFFECT.equals(type) && !TYPE_GROUP.equals(type);
    }

    @Nullable
    private Box calculateBounds(SceneObject object, String type) {
        if (TYPE_ZONE.equals(type)) {
            return extractZoneBounds(object);
        }

        Map<String, Object> props = object.getProperties();
        Vec3d position = PropertyUtils.getVec3(props, PROP_POSITION, null);

        if (position == null) {
            return null;
        }

        Vec3d scale = PropertyUtils.getVec3(props, PROP_SCALE, new Vec3d(1, 1, 1));

        double extX = Math.max(0.1, Math.abs(scale.x) * 0.5);
        double extY = Math.max(0.1, Math.abs(scale.y) * 0.5);
        double extZ = Math.max(0.1, Math.abs(scale.z) * 0.5);

        switch (type) {
            case TYPE_LIGHT -> {
                double r = PropertyUtils.getDouble(props, PROP_RADIUS, 0.35);
                double size = Math.max(0.2, r);
                extX = extY = extZ = size;
            }
            case TYPE_MARKER -> extX = extY = extZ = 0.25;
            case TYPE_PARTICLE, TYPE_CAMERA -> extX = extY = extZ = 0.4;
        }

        return new Box(
                position.x - extX, position.y - extY, position.z - extZ,
                position.x + extX, position.y + extY, position.z + extZ
        );
    }

    @Nullable
    private Box extractZoneBounds(SceneObject object) {
        Map<String, Object> props = object.getProperties();

        Object c1Raw = props.get(PROP_CORNER_1);
        Object c2Raw = props.get(PROP_CORNER_2);

        if (!(c1Raw instanceof Map) || !(c2Raw instanceof Map)) {
            return null;
        }

        Map<?, ?> c1 = (Map<?, ?>) c1Raw;
        Map<?, ?> c2 = (Map<?, ?>) c2Raw;

        double x1 = PropertyUtils.getDouble(c1, PROP_X, 0);
        double y1 = PropertyUtils.getDouble(c1, PROP_Y, 0);
        double z1 = PropertyUtils.getDouble(c1, PROP_Z, 0);

        double x2 = PropertyUtils.getDouble(c2, PROP_X, 0);
        double y2 = PropertyUtils.getDouble(c2, PROP_Y, 0);
        double z2 = PropertyUtils.getDouble(c2, PROP_Z, 0);

        return new Box(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }

    @Nullable
    private Vec3d intersectBox(Vec3d start, Vec3d end, Box box) {
        Vec3d dir = end.subtract(start).normalize();

        double tMin = 0.0;
        double tMax = start.distanceTo(end);

        // X
        if (Math.abs(dir.x) < EPSILON) {
            if (start.x < box.minX || start.x > box.maxX) return null;
        } else {
            double invDir = 1.0 / dir.x;
            double t1 = (box.minX - start.x) * invDir;
            double t2 = (box.maxX - start.x) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Y
        if (Math.abs(dir.y) < EPSILON) {
            if (start.y < box.minY || start.y > box.maxY) return null;
        } else {
            double invDir = 1.0 / dir.y;
            double t1 = (box.minY - start.y) * invDir;
            double t2 = (box.maxY - start.y) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Z
        if (Math.abs(dir.z) < EPSILON) {
            if (start.z < box.minZ || start.z > box.maxZ) return null;
        } else {
            double invDir = 1.0 / dir.z;
            double t1 = (box.minZ - start.z) * invDir;
            double t2 = (box.maxZ - start.z) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (tMax >= tMin && tMin >= 0) {
            return start.add(dir.multiply(tMin));
        }

        return null;
    }

    @Nullable
    private Ray computeScreenRay(MinecraftClient client, Camera camera) {
        Window window = client.getWindow();
        if (window.getWidth() <= 0 || window.getHeight() <= 0) return null;

        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();

        float ndcX = (float) ((mouseX / window.getWidth()) * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (mouseY / window.getHeight()) * 2.0);

        Matrix4f viewMatrix = new Matrix4f();
        Matrix4f projMatrix = new Matrix4f();

        if (!WorldViewCapture.copyMatrices(viewMatrix, projMatrix)) {
            return null;
        }

        Matrix4f inverseTransform = new Matrix4f(projMatrix).mul(viewMatrix).invert();

        Vector4f nearPoint = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f farPoint = new Vector4f(ndcX, ndcY, 1f, 1f);

        inverseTransform.transform(nearPoint);
        inverseTransform.transform(farPoint);

        if (Math.abs(nearPoint.w) < EPSILON || Math.abs(farPoint.w) < EPSILON) return null;

        nearPoint.div(nearPoint.w);
        farPoint.div(farPoint.w);

        Vec3d start = new Vec3d(nearPoint.x, nearPoint.y, nearPoint.z);
        Vec3d end = new Vec3d(farPoint.x, farPoint.y, farPoint.z);
        Vec3d direction = end.subtract(start);

        if (direction.lengthSquared() < EPSILON) return null;

        return new Ray(start, direction.normalize());
    }

    private record Ray(Vec3d origin, Vec3d direction) {}

    private static final class Intersection {
        double distSq;
        @Nullable Vec3d position;

        Intersection(double distSq, @Nullable Vec3d position) {
            this.distSq = distSq;
            this.position = position;
        }

        void update(double newDistSq, Vec3d newPosition) {
            this.distSq = newDistSq;
            this.position = newPosition;
        }
    }


    private static final class PropertyUtils {
        private PropertyUtils() {}

        static double getDouble(Map<?, ?> map, String key, double fallback) {
            if (map == null) return fallback;
            Object val = map.get(key);
            if (val instanceof Number n) return n.doubleValue();
            if (val == null) return fallback;
            try {
                return Double.parseDouble(val.toString());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        @Nullable
        static Vec3d getVec3(Map<String, Object> props, String key, @Nullable Vec3d fallback) {
            Object obj = props.get(key);
            if (!(obj instanceof Map)) return fallback;

            Map<?, ?> map = (Map<?, ?>) obj;
            double x = getDouble(map, PROP_X, fallback != null ? fallback.x : 0);
            double y = getDouble(map, PROP_Y, fallback != null ? fallback.y : 0);
            double z = getDouble(map, PROP_Z, fallback != null ? fallback.z : 0);

            return new Vec3d(x, y, z);
        }
    }
}