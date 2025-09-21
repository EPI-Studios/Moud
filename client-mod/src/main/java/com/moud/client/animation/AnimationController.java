package com.moud.client.animation;

import com.moud.client.player.ClientPlayerModelManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationController {
    private final ClientPlayerModelManager.ManagedPlayerModel model;
    private AnimationData currentAnimation;
    private float animationTime;
    private boolean isPlaying;
    private final Map<String, AnimationData> loadedAnimations = new ConcurrentHashMap<>();

    public AnimationController(ClientPlayerModelManager.ManagedPlayerModel model) {
        this.model = model;
    }

    public void loadAnimation(String name, String jsonContent) {
        AnimationData animation = AnimationDataParser.parseAnimation(jsonContent);
        if (animation != null) {
            loadedAnimations.put(name, animation);
        }
    }

    public void playAnimation(String animationName) {
        AnimationData animation = loadedAnimations.get(animationName);
        if (animation != null) {
            playAnimation(animation);
        }
    }

    public void playAnimation(AnimationData animation) {
        this.currentAnimation = animation;
        this.animationTime = 0;
        this.isPlaying = true;
    }

    public void stopAnimation() {
        this.isPlaying = false;
        this.currentAnimation = null;
    }

    public void tick(float delta) {
        if (!isPlaying || currentAnimation == null) return;

        animationTime += delta * 0.05f;
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
        if (model == null) return;

        currentAnimation.getBones().forEach((boneName, boneAnimation) -> {
            ModelPart part = getModelPart(boneName);
            if (part != null) {
                Vector3f rotation = interpolate(boneAnimation.getRotation());
                Vector3f position = interpolate(boneAnimation.getPosition());

                part.pitch = (float) Math.toRadians(rotation.x());
                part.yaw = (float) Math.toRadians(rotation.y());
                part.roll = (float) Math.toRadians(rotation.z());

                if (position.lengthSquared() > 0) {
                    part.pivotX += position.x();
                    part.pivotY += position.y();
                    part.pivotZ += position.z();
                }
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
        try {
            switch (boneName) {
                case "head": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().head;
                case "body": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().body;
                case "right_arm": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().rightArm;
                case "left_arm": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().leftArm;
                case "right_leg": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().rightLeg;
                case "left_leg": return ((ClientPlayerModelManager.ManagedPlayerModel) model).getModel().leftLeg;
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentAnimationName() {
        if (currentAnimation == null) return null;
        return loadedAnimations.entrySet().stream()
                .filter(entry -> entry.getValue().equals(currentAnimation))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}