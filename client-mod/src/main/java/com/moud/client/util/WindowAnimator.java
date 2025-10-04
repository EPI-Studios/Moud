package com.moud.client.util;

import com.moud.api.math.Easing;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;

public class WindowAnimator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowAnimator.class);

    private static boolean isAnimating = false;
    private static long startTime;
    private static int duration;

    private static int startX, startY, startWidth, startHeight;

    private static int targetX, targetY, targetWidth, targetHeight;

    private static int originalX, originalY, originalWidth, originalHeight;
    private static boolean originalStateSaved = false;

    private static Function<Double, Double> easingFunction;

    private static final Queue<AnimationStep> animationQueue = new LinkedList<>();

    private record AnimationStep(int tX, int tY, int tW, int tH, int dur, String easing, boolean isRestore) {}

    public static void startSequence(List<Map<String, Object>> steps) {
        animationQueue.clear();
        originalStateSaved = false;

        for (Map<String, Object> stepData : steps) {
            int tX = stepData.get("x") != null ? ((Number)stepData.get("x")).intValue() : -1;
            int tY = stepData.get("y") != null ? ((Number)stepData.get("y")).intValue() : -1;
            int tW = stepData.get("width") != null ? ((Number)stepData.get("width")).intValue() : -1;
            int tH = stepData.get("height") != null ? ((Number)stepData.get("height")).intValue() : -1;
            int dur = stepData.get("duration") != null ? ((Number)stepData.get("duration")).intValue() : 500;
            String easing = stepData.get("easing") != null ? (String)stepData.get("easing") : "ease-out-quad";
            boolean isRestore = stepData.get("restore") != null && (Boolean)stepData.get("restore");

            animationQueue.add(new AnimationStep(tX, tY, tW, tH, dur, easing, isRestore));
        }

        if (!isAnimating) {
            startNextAnimationInQueue();
        }
    }

    public static void startAnimation(int tX, int tY, int tW, int tH, int dur, String easing) {
        animationQueue.clear();
        originalStateSaved = false;
        animationQueue.add(new AnimationStep(tX, tY, tW, tH, dur, easing, false));
        if (!isAnimating) {
            startNextAnimationInQueue();
        }
    }

    public static void restore(int dur, String easing) {
        if (!originalStateSaved) {
            LOGGER.warn("WindowAnimator.restore() a été appelé sans qu'aucune animation n'ait été lancée au préalable. Impossible de restaurer.");
            return;
        }
        animationQueue.clear();
        animationQueue.add(new AnimationStep(0, 0, 0, 0, dur, easing, true));
        if (!isAnimating) {
            startNextAnimationInQueue();
        }
    }

    private static void startNextAnimationInQueue() {
        if (animationQueue.isEmpty()) {
            isAnimating = false;
            originalStateSaved = false;
            return;
        }

        AnimationStep nextStep = animationQueue.poll();
        MinecraftClient client = MinecraftClient.getInstance();
        long handle = client.getWindow().getHandle();

        if (!originalStateSaved) {
            int[] xpos = new int[1];
            int[] ypos = new int[1];
            GLFW.glfwGetWindowPos(handle, xpos, ypos);
            originalX = xpos[0];
            originalY = ypos[0];

            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetWindowSize(handle, width, height);
            originalWidth = width[0];
            originalHeight = height[0];
            originalStateSaved = true;
        }

        int[] xpos = new int[1];
        int[] ypos = new int[1];
        GLFW.glfwGetWindowPos(handle, xpos, ypos);
        startX = xpos[0];
        startY = ypos[0];

        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowSize(handle, width, height);
        startWidth = width[0];
        startHeight = height[0];

        if (nextStep.isRestore()) {
            targetX = originalX;
            targetY = originalY;
            targetWidth = originalWidth;
            targetHeight = originalHeight;
        } else {
            targetX = (nextStep.tX == -1) ? startX : nextStep.tX;
            targetY = (nextStep.tY == -1) ? startY : nextStep.tY;
            targetWidth = (nextStep.tW == -1) ? startWidth : nextStep.tW;
            targetHeight = (nextStep.tH == -1) ? startHeight : nextStep.tH;
        }

        duration = Math.max(1, nextStep.dur);
        startTime = System.currentTimeMillis();
        easingFunction = Easing.get(nextStep.easing);
        isAnimating = true;
    }

    public static void tick() {
        if (!isAnimating) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double progress = Math.min((double) elapsedTime / duration, 1.0);
        double easedProgress = easingFunction.apply(progress);

        final int newX = (int) (startX + (targetX - startX) * easedProgress);
        final int newY = (int) (startY + (targetY - startY) * easedProgress);
        final int newWidth = (int) (startWidth + (targetWidth - startWidth) * easedProgress);
        final int newHeight = (int) (startHeight + (targetHeight - startHeight) * easedProgress);

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            long handle = client.getWindow().getHandle();
            if (handle != 0) {
                GLFW.glfwSetWindowPos(handle, newX, newY);
                GLFW.glfwSetWindowSize(handle, newWidth, newHeight);
            }
        });

        if (progress >= 1.0) {
            startNextAnimationInQueue();
        }
    }
}