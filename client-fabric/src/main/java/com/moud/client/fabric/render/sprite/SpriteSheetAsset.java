package com.moud.client.fabric.render.sprite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record SpriteSheetAsset(
        List<Frame> frames,
        Map<String, Animation> animations,
        int textureWidth,
        int textureHeight
) {

    private static final String DEFAULT_ANIMATION = "default";

    public static SpriteSheetAsset parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("spritesheet json is empty");
        }

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject meta = root.has("meta") && root.get("meta").isJsonObject()
                ? root.getAsJsonObject("meta")
                : new JsonObject();
        JsonObject size = meta.has("size") && meta.get("size").isJsonObject()
                ? meta.getAsJsonObject("size")
                : new JsonObject();

        int textureWidth = Math.max(1, intValue(size, "w", 1));
        int textureHeight = Math.max(1, intValue(size, "h", 1));

        List<Frame> frames = parseFrames(root.get("frames"));
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("spritesheet json contains no frames");
        }

        Map<String, Animation> animations = parseAnimations(meta.get("frameTags"), frames.size());
        animations.putIfAbsent(DEFAULT_ANIMATION, new Animation(DEFAULT_ANIMATION, forwardIndices(0, frames.size() - 1)));

        return new SpriteSheetAsset(List.copyOf(frames), Map.copyOf(animations), textureWidth, textureHeight);
    }

    public Animation animation(String requestedName) {
        if (requestedName != null) {
            Animation named = animations.get(requestedName.trim());
            if (named != null) {
                return named;
            }
        }
        Animation fallback = animations.get(DEFAULT_ANIMATION);
        return fallback != null ? fallback : animations.values().iterator().next();
    }

    public Frame frame(Animation animation, long timeMs, boolean playing, boolean loop, float speedScale, int explicitFrame) {
        if (frames.isEmpty()) {
            return null;
        }

        if (!playing) {
            return frames.get(clampIndex(explicitFrame));
        }

        List<Integer> indices = animation == null ? List.of(0) : animation.frameIndices();
        if (indices.isEmpty()) {
            return frames.get(0);
        }
        if (indices.size() == 1) {
            return frames.get(clampIndex(indices.getFirst()));
        }

        float safeSpeed = Math.max(0.001f, Math.abs(speedScale));
        long scaledTimeMs = Math.max(0L, (long) (timeMs * safeSpeed));
        int totalDuration = 0;
        for (int frameIndex : indices) {
            totalDuration += Math.max(1, frames.get(clampIndex(frameIndex)).durationMs());
        }
        if (totalDuration <= 0) {
            return frames.get(clampIndex(indices.getFirst()));
        }

        long cursor = loop ? Math.floorMod(scaledTimeMs, totalDuration) : Math.min(scaledTimeMs, Math.max(0, totalDuration - 1L));
        for (int frameIndex : indices) {
            Frame frame = frames.get(clampIndex(frameIndex));
            int duration = Math.max(1, frame.durationMs());
            if (cursor < duration) {
                return frame;
            }
            cursor -= duration;
        }
        return frames.get(clampIndex(indices.getLast()));
    }

    private int clampIndex(int index) {
        return Math.max(0, Math.min(frames.size() - 1, index));
    }

    private static List<Frame> parseFrames(JsonElement framesElement) {
        ArrayList<Frame> frames = new ArrayList<>();
        if (framesElement == null || framesElement.isJsonNull()) {
            return frames;
        }
        if (framesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : framesElement.getAsJsonObject().entrySet()) {
                Frame frame = parseFrame(entry.getKey(), entry.getValue());
                if (frame != null) {
                    frames.add(frame);
                }
            }
            return frames;
        }
        if (framesElement.isJsonArray()) {
            JsonArray array = framesElement.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                Frame frame = parseFrame("frame_" + i, array.get(i));
                if (frame != null) {
                    frames.add(frame);
                }
            }
        }
        return frames;
    }

    private static Frame parseFrame(String name, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Rect frameRect = rect(object.get("frame"));
        Rect spriteSourceRect = rect(object.get("spriteSourceSize"));
        JsonObject sourceSize = object.has("sourceSize") && object.get("sourceSize").isJsonObject()
                ? object.getAsJsonObject("sourceSize")
                : new JsonObject();
        return new Frame(
                name,
                frameRect,
                spriteSourceRect,
                Math.max(1, intValue(sourceSize, "w", frameRect.w())),
                Math.max(1, intValue(sourceSize, "h", frameRect.h())),
                Math.max(1, intValue(object, "duration", 100)),
                boolValue(object, "rotated", false),
                boolValue(object, "trimmed", false)
        );
    }

    private static LinkedHashMap<String, Animation> parseAnimations(JsonElement tagsElement, int frameCount) {
        LinkedHashMap<String, Animation> animations = new LinkedHashMap<>();
        if (tagsElement == null || !tagsElement.isJsonArray()) {
            return animations;
        }
        for (JsonElement element : tagsElement.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject tag = element.getAsJsonObject();
            String name = stringValue(tag, "name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            int from = Math.max(0, intValue(tag, "from", 0));
            int to = Math.max(from, Math.min(frameCount - 1, intValue(tag, "to", from)));
            String direction = stringValue(tag, "direction", "forward").toLowerCase(Locale.ROOT);
            List<Integer> indices = switch (direction) {
                case "reverse" -> reverseIndices(from, to);
                case "pingpong" -> pingPongIndices(from, to);
                default -> forwardIndices(from, to);
            };
            animations.put(name, new Animation(name, List.copyOf(indices)));
        }
        return animations;
    }

    private static List<Integer> forwardIndices(int from, int to) {
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            indices.add(i);
        }
        return indices;
    }

    private static List<Integer> reverseIndices(int from, int to) {
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = to; i >= from; i--) {
            indices.add(i);
        }
        return indices;
    }

    private static List<Integer> pingPongIndices(int from, int to) {
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            indices.add(i);
        }
        for (int i = Math.max(from + 1, to - 1); i > from; i--) {
            indices.add(i);
        }
        return indices;
    }

    private static Rect rect(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new Rect(0, 0, 1, 1);
        }
        JsonObject object = element.getAsJsonObject();
        return new Rect(
                intValue(object, "x", 0),
                intValue(object, "y", 0),
                Math.max(1, intValue(object, "w", 1)),
                Math.max(1, intValue(object, "h", 1))
        );
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record Rect(int x, int y, int w, int h) {}

    public record Frame(
            String name,
            Rect frameRect,
            Rect spriteSourceRect,
            int sourceWidth,
            int sourceHeight,
            int durationMs,
            boolean rotated,
            boolean trimmed
    ) {}

    public record Animation(String name, List<Integer> frameIndices) {}
}
