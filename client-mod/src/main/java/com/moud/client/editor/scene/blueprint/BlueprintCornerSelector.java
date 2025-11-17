package com.moud.client.editor.scene.blueprint;

import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.scene.SceneEditorDiagnostics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BlueprintCornerSelector {
    public enum Corner { A, B }

    private static final BlueprintCornerSelector INSTANCE = new BlueprintCornerSelector();

    public static BlueprintCornerSelector getInstance() {
        return INSTANCE;
    }

    private Corner pendingCorner;
    private Consumer<float[]> callback;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private BlueprintCornerSelector() {}

    public boolean isPicking() {
        return active.get();
    }

    public Corner getPendingCorner() {
        return pendingCorner;
    }

    public void beginSelection(Corner corner, Consumer<float[]> callback) {
        this.pendingCorner = corner;
        this.callback = callback;
        this.active.set(true);
        SceneEditorDiagnostics.log("Click to set corner " + corner);
    }

    public void cancel() {
        pendingCorner = null;
        callback = null;
        active.set(false);
    }

    public boolean handleMouseButton(int button, int action) {
        if (!active.get() || pendingCorner == null || callback == null) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return false;
        }
        float[] picked = pickCurrentTarget();
        if (picked != null) {
            callback.accept(picked);
            SceneEditorDiagnostics.log("Corner " + pendingCorner + " set at %.2f, %.2f, %.2f".formatted(picked[0], picked[1], picked[2]));
            cancel();
        } else {
            SceneEditorDiagnostics.log("No valid target for corner " + pendingCorner);
        }
        return true;
    }

    private float[] pickCurrentTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }

        HitResult hitResult = client.crosshairTarget;
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            HitResult manual = cameraEntity.raycast(256.0, 1.0f, false);
            if (manual != null && manual.getType() != HitResult.Type.MISS) {
                hitResult = manual;
            }
        }
        Vec3d position = null;
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            if (hitResult instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                Vec3d hitPos = blockHit.getPos();
                if (hitPos != null) {
                    position = hitPos;
                } else {
                    position = Vec3d.ofCenter(pos);
                }
            } else if (hitResult instanceof EntityHitResult entityHit) {
                position = entityHit.getEntity().getPos();
            }
        }

        if (position == null) {
            RaycastPicker picker = RaycastPicker.getInstance();
            picker.updateHover();
            RuntimeObject hovered = picker.getHoveredObject();
            if (hovered != null) {
                position = hovered.getPosition();
            }
        }

        if (position == null) {
            return null;
        }
        return new float[]{(float) position.x, (float) position.y, (float) position.z};
    }
}
