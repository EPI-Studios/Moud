package com.moud.client.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class AnimationDataParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationDataParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AnimationData parseAnimation(String jsonContent) {
        try {
            JsonNode root = MAPPER.readTree(jsonContent);

            String formatVersion = root.path("format_version").asText("1.8.0");
            JsonNode animationsNode = root.path("animations");

            if (animationsNode.isEmpty()) {
                LOGGER.warn("No animations found in JSON");
                return null;
            }

            Iterator<String> animationNames = animationsNode.fieldNames();
            if (!animationNames.hasNext()) {
                LOGGER.warn("No animation definitions found");
                return null;
            }

            String firstAnimationName = animationNames.next();
            JsonNode animationNode = animationsNode.path(firstAnimationName);

            return parseAnimationNode(animationNode);

        } catch (Exception e) {
            LOGGER.error("Failed to parse animation JSON", e);
            return null;
        }
    }

    private static AnimationData parseAnimationNode(JsonNode animationNode) {
        boolean loop = animationNode.path("loop").asBoolean(false);
        float length = (float) animationNode.path("animation_length").asDouble(1.0);

        Map<String, AnimationData.BoneAnimation> bones = new HashMap<>();
        JsonNode bonesNode = animationNode.path("bones");

        Iterator<String> boneNames = bonesNode.fieldNames();
        while (boneNames.hasNext()) {
            String boneName = boneNames.next();
            JsonNode boneNode = bonesNode.path(boneName);

            AnimationData.BoneAnimation boneAnimation = parseBoneAnimation(boneNode);
            if (boneAnimation != null) {
                bones.put(boneName, boneAnimation);
            }
        }

        return new AnimationData(length, loop, bones);
    }

    private static AnimationData.BoneAnimation parseBoneAnimation(JsonNode boneNode) {
        TreeMap<Float, float[]> rotation = parseKeyframes(boneNode.path("rotation"));
        TreeMap<Float, float[]> position = parseKeyframes(boneNode.path("position"));
        TreeMap<Float, float[]> scale = parseKeyframes(boneNode.path("scale"));

        return new AnimationData.BoneAnimation(rotation, position, scale);
    }

    private static TreeMap<Float, float[]> parseKeyframes(JsonNode keyframesNode) {
        TreeMap<Float, float[]> keyframes = new TreeMap<>();

        if (keyframesNode.isEmpty()) {
            return keyframes;
        }

        Iterator<String> timeKeys = keyframesNode.fieldNames();
        while (timeKeys.hasNext()) {
            String timeStr = timeKeys.next();
            try {
                float time = Float.parseFloat(timeStr);
                JsonNode keyframeNode = keyframesNode.path(timeStr);

                JsonNode postNode = keyframeNode.path("post");
                if (postNode.isArray() && postNode.size() >= 3) {
                    float[] values = new float[3];
                    values[0] = (float) postNode.get(0).asDouble(0.0);
                    values[1] = (float) postNode.get(1).asDouble(0.0);
                    values[2] = (float) postNode.get(2).asDouble(0.0);
                    keyframes.put(time, values);
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid time key: {}", timeStr);
            }
        }

        return keyframes;
    }
}