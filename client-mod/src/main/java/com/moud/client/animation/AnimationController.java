package com.moud.client.animation;

import com.moud.client.player.ClientPlayerModelManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.Map;
import java.util.TreeMap;

public class AnimationController {
    private final ClientPlayerModelManager.ManagedPlayerModel model;
    private AnimationData currentAnimation;
    private float animationTime;
    private boolean isPlaying;

    public AnimationController(ClientPlayerModelManager.ManagedPlayerModel model) {
        this.model = model;
    }

    public void playAnimation(AnimationData animation) {
        this.currentAnimation = animation;
        this.animationTime = 0;
        this.isPlaying = true;
    }

    public void tick(float delta) {
        if (!isPlaying || currentAnimation == null) return;

        animationTime += delta;
        if (animationTime > currentAnimation.getLength()) {
            if (currentAnimation.isLooping()) {
                animationTime %= currentAnimation.getLength();
            } else {
                isPlaying = false;
                return;
            }
        }

        updateModelParts();
    }

    private void updateModelParts() {
        if (model.getModel() == null) return;

        currentAnimation.getBones().forEach((boneName, boneAnimation) -> {
            ModelPart part = getModelPart(boneName);
            if (part != null) {
                Vector3f rotation = interpolate(boneAnimation.getRotation());
                part.pitch = (float) Math.toRadians(rotation.x());
                part.yaw = (float) Math.toRadians(rotation.y());
                part.roll = (float) Math.toRadians(rotation.z());
            }
        });
    }

    private Vector3f interpolate(TreeMap<Float, float[]> keyframes) {
        if (keyframes == null || keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        Map.Entry<Float, float[]> floorEntry = keyframes.floorEntry(animationTime);
        Map.Entry<Float, float[]> ceilEntry = keyframes.ceilingEntry(animationTime);

        if (floorEntry == null) return new Vector3f(ceilEntry.getValue()[0], ceilEntry.getValue()[1], ceilEntry.getValue()[2]);
        if (ceilEntry == null) return new Vector3f(floorEntry.getValue()[0], floorEntry.getValue()[1], floorEntry.getValue()[2]);

        if (floorEntry.equals(ceilEntry)) {
            return new Vector3f(floorEntry.getValue()[0], floorEntry.getValue()[1], floorEntry.getValue()[2]);
        }

        float timeBetweenFrames = ceilEntry.getKey() - floorEntry.getKey();
        float progress = (animationTime - floorEntry.getKey()) / timeBetweenFrames;

        float x = MathHelper.lerp(progress, floorEntry.getValue()[0], ceilEntry.getValue()[0]);
        float y = MathHelper.lerp(progress, floorEntry.getValue()[1], ceilEntry.getValue()[1]);
        float z = MathHelper.lerp(progress, floorEntry.getValue()[2], ceilEntry.getValue()[2]);

        return new Vector3f(x, y, z);
    }

    private ModelPart getModelPart(String boneName) {
        switch (boneName) {
            case "head": return model.getModel().head;
            case "body": return model.getModel().body;
            case "right_arm": return model.getModel().rightArm;
            case "left_arm": return model.getModel().leftArm;
            case "right_leg": return model.getModel().rightLeg;
            case "left_leg": return model.getModel().leftLeg;
            default: return null;
        }
    }
}