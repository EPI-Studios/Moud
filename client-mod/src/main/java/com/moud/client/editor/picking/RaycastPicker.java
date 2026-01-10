package com.moud.client.editor.picking;

import com.moud.client.editor.runtime.Capsule;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.animation.AnimatedPlayerModel;
import com.moud.client.animation.ClientFakePlayerManager;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.util.LimbRaycaster;
import com.moud.client.editor.runtime.RuntimeObjectType;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.editor.ui.WorldViewCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class RaycastPicker {
    private static final RaycastPicker INSTANCE = new RaycastPicker();
    private static final double MAX_REACH = 100.0;

    private RuntimeObject hoveredObject;
    private RuntimeObject selectedObject;
    private String hoveredLimb;
    private String selectedLimb;
    private double hoveredDistanceSq = Double.MAX_VALUE;
    private String debugLastObj;
    private String debugLastLimb;
    private String debugLastSource;

    private RaycastPicker() {}

    public static RaycastPicker getInstance() {
        return INSTANCE;
    }

    public void updateHover() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getCameraEntity() == null) {
            resetHover();
            return;
        }

        if (EditorImGuiLayer.getInstance().isMouseOverUI()) {
            resetHover();
            return;
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
            Vector3f forward = new Vector3f(0, 0, 1);
            camera.getRotation().transform(forward);
            Vec3d fallback = new Vec3d(forward.x, forward.y, forward.z);
            if (fallback.lengthSquared() < 1.0E-6) {
                resetHover();
                return;
            }
            rayDirection = fallback.normalize();
        }
        if (rayDirection == null) {
            resetHover();
            return;
        }
        Vec3d rayEnd = rayStart.add(rayDirection.multiply(MAX_REACH));

        resetHover();
        raycastObjects(rayStart, rayEnd, rayDirection);
    }

    private void resetHover() {
        hoveredObject = null;
        hoveredLimb = null;
        hoveredDistanceSq = Double.MAX_VALUE;
    }

    public void selectHovered() {
        if (hoveredObject != null) {
            selectedObject = hoveredObject;
            selectedLimb = hoveredLimb;
        }
    }

    public void setSelectedObject(@Nullable RuntimeObject runtimeObject) {
        this.selectedObject = runtimeObject;
        this.selectedLimb = null;
    }

    public void setSelection(@Nullable RuntimeObject runtimeObject, @Nullable String limbId) {
        this.selectedObject = runtimeObject;
        this.selectedLimb = limbId;
    }

    public void clearSelection() {
        selectedObject = null;
        selectedLimb = null;
    }

    @Nullable
    public RuntimeObject getHoveredObject() {
        return hoveredObject;
    }

    @Nullable
    public RuntimeObject getSelectedObject() {
        return selectedObject;
    }

    @Nullable
    public String getHoveredLimb() {
        return hoveredLimb;
    }

    @Nullable
    public String getSelectedLimb() {
        return selectedLimb;
    }

    public boolean hasSelection() {
        return selectedObject != null;
    }

    @Nullable
    private void raycastObjects(Vec3d rayStart, Vec3d rayEnd, Vec3d rayDir) {
        RuntimeObject closest = null;
        double closestDistance = Double.MAX_VALUE;
        String closestLimb = null;

        for (RuntimeObject obj : RuntimeObjectRegistry.getInstance().getObjects()) {
            double hitDistance = Double.MAX_VALUE;
            String detectedLimb = null;
            boolean hitFound = false;

            if (obj.getType() == RuntimeObjectType.PLAYER_MODEL) {
                long modelId = parseModelId(obj.getObjectId());
                float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);

                OtherClientPlayerEntity fakePlayer = ClientFakePlayerManager.getInstance().getFakePlayer(modelId);
                if (fakePlayer != null) {
                    LimbRaycaster.LimbHit hit = LimbRaycaster.raycast(fakePlayer, rayStart, rayDir, tickDelta);
                    if (hit != null) {
                        hitDistance = hit.distance() * hit.distance();
                        detectedLimb = hit.boneName();
                        hitFound = true;
                    }
                } else {
                    AnimatedPlayerModel animModel = ClientPlayerModelManager.getInstance().getModel(modelId);
                    if (animModel != null) {
                        LimbRaycaster.LimbHit hit = LimbRaycaster.raycast(animModel, rayStart, rayDir, tickDelta);
                        if (hit != null) {
                            hitDistance = hit.distance() * hit.distance();
                            detectedLimb = hit.boneName();
                            hitFound = true;
                        }
                    }
                }
            } else {
                if (obj.getType() == RuntimeObjectType.MODEL) {
                    long modelId = parseModelId(obj.getObjectId());
                    if (modelId >= 0 && ClientCollisionManager.hasMesh(modelId)) {
                        ClientCollisionManager.RaycastHit meshHit = ClientCollisionManager.raycastModel(modelId, rayStart, rayDir, MAX_REACH);
                        if (meshHit != null) {
                            hitDistance = meshHit.distance() * meshHit.distance();
                            detectedLimb = null;
                            hitFound = true;
                        }
                    }
                }
                Box bounds = obj.getBounds();
                if (bounds != null) {
                    Vec3d hit = raycastBox(rayStart, rayEnd, bounds);
                    if (hit != null) {
                        hitDistance = rayStart.squaredDistanceTo(hit);
                        detectedLimb = null;
                        hitFound = true;
                    }
                }
            }

            if (hitFound && hitDistance < closestDistance) {
                closestDistance = hitDistance;
                closest = obj;
                closestLimb = detectedLimb;
            }
        }

        hoveredDistanceSq = closestDistance;
        hoveredObject = closest;
        hoveredLimb = closestLimb;

    }

    private HitResult raycastCapsule(Vec3d rayStart, Vec3d rayEnd, Capsule capsule) {
        Vec3d dir = rayEnd.subtract(rayStart);
        double lenSq = dir.lengthSquared();
        if (lenSq < 1e-6) return null;
        Vec3d rd = dir.normalize();

        Vec3d a = capsule.a();
        Vec3d b = capsule.b();
        Vec3d ab = b.subtract(a);
        double abLenSq = ab.lengthSquared();

        Vec3d ao = rayStart.subtract(a);
        double abDotRd = ab.dotProduct(rd);
        double abDotAo = ab.dotProduct(ao);
        double denom = abLenSq - abDotRd * abDotRd;

        double tRay;
        if (Math.abs(denom) < 1e-6) {
            tRay = -ao.dotProduct(rd);
        } else {
            tRay = (abLenSq * -ao.dotProduct(rd) + abDotAo * abDotRd) / denom;
        }
        if (tRay < 0) return null;

        Vec3d p = rayStart.add(rd.multiply(tRay));

        double tSeg = abLenSq < 1e-6 ? 0 : Math.max(0, Math.min(1, ab.dotProduct(p.subtract(a)) / abLenSq));
        Vec3d c = a.add(ab.multiply(tSeg));

        double distSq = p.squaredDistanceTo(c);
        if (distSq <= capsule.radius() * capsule.radius()) {
            return new HitResult(p, distSq);
        }
        return null;
    }

    private record HitResult(Vec3d point, double distanceSq) {}

    private long parseModelId(String objId) {
        if (objId == null) return -1;
        int colon = objId.indexOf(':');
        if (colon >= 0 && colon < objId.length() - 1) {
            try {
                return Long.parseLong(objId.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    @Nullable
    private Ray computeMouseRay(Camera camera) {
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

        Vector3f fallback = new Vector3f(0, 0, 1);
        camera.getRotation().transform(fallback);
        Vec3d ray = new Vec3d(fallback.x, fallback.y, fallback.z);
        double lenSq = ray.lengthSquared();
        if (lenSq < 1.0E-6) {
            return null;
        }
        return new Ray(camera.getPos(), ray.normalize());
    }

    @Nullable
    private Vec3d raycastBox(Vec3d rayStart, Vec3d rayEnd, Box box) {
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

    private record Ray(Vec3d origin, Vec3d direction) {}
}
