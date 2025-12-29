package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.EventKeyframe;
import com.moud.api.animation.Keyframe;
import com.moud.api.animation.ObjectTrack;
import com.moud.api.animation.PropertyTrack;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.ui.SceneEditorOverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Recording {
    private Recording() {
    }

    public record ClipUpdate(AnimationClip clip, boolean changed) {
    }

    public record InsertResult(AnimationClip clip, String message) {
    }

    public static ClipUpdate recordTransform(
            AnimationClip clip,
            boolean recording,
            Map<String, TransformSnapshot> lastRecordedTransforms,
            SceneObject object,
            float timeSeconds,
            float[] translation,
            float[] rotation,
            float[] scale
    ) {
        if (clip == null || object == null || !recording) {
            return new ClipUpdate(clip, false);
        }

        MutableClip mutable = ensureMutableClip(clip);
        clip = mutable.clip();
        boolean changed = mutable.changed();

        ObjectTrack objTrack = ensureObjectTrack(clip, object);
        if (objTrack == null) {
            return new ClipUpdate(clip, changed);
        }

        TransformSnapshot last = lastRecordedTransforms.get(object.getId());
        boolean firstRecord = last == null;
        float eps = 1e-4f;
        if (firstRecord) {
            last = TransformSnapshot.from(translation, rotation, scale);
        }

        boolean anyKeyframeChanged = false;
        if (firstRecord || Math.abs(translation[0] - last.translation()[0]) > eps) {
            addOrUpdateKey(objTrack, "position.x", translation[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(translation[1] - last.translation()[1]) > eps) {
            addOrUpdateKey(objTrack, "position.y", translation[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(translation[2] - last.translation()[2]) > eps) {
            addOrUpdateKey(objTrack, "position.z", translation[2], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[0] - last.rotation()[0]) > eps) {
            addOrUpdateKey(objTrack, "rotation.x", rotation[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[1] - last.rotation()[1]) > eps) {
            addOrUpdateKey(objTrack, "rotation.y", rotation[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[2] - last.rotation()[2]) > eps) {
            addOrUpdateKey(objTrack, "rotation.z", rotation[2], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[0] - last.scale()[0]) > eps) {
            addOrUpdateKey(objTrack, "scale.x", scale[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[1] - last.scale()[1]) > eps) {
            addOrUpdateKey(objTrack, "scale.y", scale[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[2] - last.scale()[2]) > eps) {
            addOrUpdateKey(objTrack, "scale.z", scale[2], timeSeconds);
            anyKeyframeChanged = true;
        }

        if (anyKeyframeChanged) {
            lastRecordedTransforms.put(object.getId(), TransformSnapshot.from(translation, rotation, scale));
        }

        return new ClipUpdate(clip, changed || anyKeyframeChanged);
    }

    public static ClipUpdate recordLimbTransform(
            AnimationClip clip,
            boolean recording,
            Map<String, TransformSnapshot> lastRecordedTransforms,
            SceneObject object,
            String limbKey,
            float timeSeconds,
            float[] translation,
            float[] rotation,
            float[] scale
    ) {
        if (clip == null || object == null || limbKey == null || !recording) {
            return new ClipUpdate(clip, false);
        }

        MutableClip mutable = ensureMutableClip(clip);
        clip = mutable.clip();
        boolean changed = mutable.changed();

        String limbPath = limbKey.startsWith("player_model:") ? limbKey : "player_model:" + limbKey;
        ObjectTrack objTrack = ensureObjectTrack(clip, object);
        if (objTrack == null) {
            return new ClipUpdate(clip, changed);
        }

        String cacheKey = object.getId() + "|" + limbPath;
        TransformSnapshot last = lastRecordedTransforms.get(cacheKey);
        boolean firstRecord = last == null;
        float eps = 1e-4f;
        if (firstRecord) {
            last = TransformSnapshot.from(translation, rotation, scale);
        }

        boolean anyKeyframeChanged = false;
        String prefix = limbPath + ".";
        if (firstRecord || Math.abs(translation[0] - last.translation()[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.x", translation[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(translation[1] - last.translation()[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.y", translation[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(translation[2] - last.translation()[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.z", translation[2], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[0] - last.rotation()[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.x", rotation[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[1] - last.rotation()[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.y", rotation[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(rotation[2] - last.rotation()[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.z", rotation[2], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[0] - last.scale()[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.x", scale[0], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[1] - last.scale()[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.y", scale[1], timeSeconds);
            anyKeyframeChanged = true;
        }
        if (firstRecord || Math.abs(scale[2] - last.scale()[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.z", scale[2], timeSeconds);
            anyKeyframeChanged = true;
        }

        if (anyKeyframeChanged) {
            lastRecordedTransforms.put(cacheKey, TransformSnapshot.from(translation, rotation, scale));
        }

        return new ClipUpdate(clip, changed || anyKeyframeChanged);
    }

    public static InsertResult insertKeyframeAtCurrentTime(
            SceneEditorOverlay overlay,
            AnimationClip clip,
            float timeSeconds
    ) {
        if (clip == null) {
            return new InsertResult(null, "No animation loaded");
        }

        SceneObject selectedObject = overlay.getSelectedObject();
        String selectedLimb = overlay.getSelectedLimbType();
        if (selectedObject == null) {
            return new InsertResult(clip, "Select an object first");
        }

        MutableClip mutable = ensureMutableClip(clip);
        clip = mutable.clip();

        ObjectTrack objTrack = ensureObjectTrack(clip, selectedObject);
        if (objTrack == null) {
            return new InsertResult(clip, "Select an object first");
        }

        float[] translation = overlay.getActiveTranslation();
        float[] rotation = overlay.getActiveRotation();
        float[] scale = overlay.getActiveScale();

        if (selectedLimb != null) {
            String limbPath = selectedLimb.startsWith("player_model:") ? selectedLimb : "player_model:" + selectedLimb;
            String prefix = limbPath + ".";
            addOrUpdateKey(objTrack, prefix + "position.x", translation[0], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "position.y", translation[1], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "position.z", translation[2], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "rotation.x", rotation[0], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "rotation.y", rotation[1], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "rotation.z", rotation[2], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "scale.x", scale[0], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "scale.y", scale[1], timeSeconds);
            addOrUpdateKey(objTrack, prefix + "scale.z", scale[2], timeSeconds);
            return new InsertResult(clip, "Added keyframes for " + selectedLimb + " at " + Playback.formatTime(timeSeconds));
        }

        addOrUpdateKey(objTrack, "position.x", translation[0], timeSeconds);
        addOrUpdateKey(objTrack, "position.y", translation[1], timeSeconds);
        addOrUpdateKey(objTrack, "position.z", translation[2], timeSeconds);
        addOrUpdateKey(objTrack, "rotation.x", rotation[0], timeSeconds);
        addOrUpdateKey(objTrack, "rotation.y", rotation[1], timeSeconds);
        addOrUpdateKey(objTrack, "rotation.z", rotation[2], timeSeconds);
        addOrUpdateKey(objTrack, "scale.x", scale[0], timeSeconds);
        addOrUpdateKey(objTrack, "scale.y", scale[1], timeSeconds);
        addOrUpdateKey(objTrack, "scale.z", scale[2], timeSeconds);
        return new InsertResult(clip, "Added 9 keyframes at " + Playback.formatTime(timeSeconds));
    }

    private record MutableClip(AnimationClip clip, boolean changed) {
    }

    private static MutableClip ensureMutableClip(AnimationClip clip) {
        List<ObjectTrack> objects = clip.objectTracks();
        if (!(objects instanceof ArrayList<ObjectTrack>)) {
            objects = new ArrayList<>(objects != null ? objects : List.of());
            List<EventKeyframe> events = clip.eventTrack() != null ? new ArrayList<>(clip.eventTrack()) : new ArrayList<>();
            return new MutableClip(
                    new AnimationClip(clip.id(), clip.name(), clip.duration(), clip.frameRate(), objects, events, clip.metadata()),
                    true
            );
        }
        return new MutableClip(clip, false);
    }

    private static ObjectTrack ensureObjectTrack(AnimationClip clip, SceneObject selected) {
        List<ObjectTrack> objects = clip.objectTracks();
        for (ObjectTrack ot : objects) {
            if (ot.targetObjectId().equals(selected.getId())) {
                return ot;
            }
        }
        Map<String, PropertyTrack> props = new LinkedHashMap<>();
        ObjectTrack newObj = new ObjectTrack(
                selected.getId(),
                selected.getProperties().getOrDefault("label", selected.getId()).toString(),
                props
        );
        objects.add(newObj);
        return newObj;
    }

    private static void addOrUpdateKey(ObjectTrack objTrack, String path, float value, float time) {
        PropertyTrack track = objTrack.propertyTracks().computeIfAbsent(path,
                p -> new PropertyTrack(p, PropertyTrack.PropertyType.FLOAT, -100000f, 100000f, new ArrayList<>()));
        List<Keyframe> keyframes = track.keyframes();
        boolean updated = false;
        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe existing = keyframes.get(i);
            if (Math.abs(existing.time() - time) < 1e-3) {
                keyframes.set(i, new Keyframe(time, value, existing.interpolation(), existing.inTangent(), existing.outTangent()));
                updated = true;
                break;
            }
        }
        if (!updated) {
            keyframes.add(new Keyframe(time, value, Keyframe.Interpolation.LINEAR, 0f, 0f));
            keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        }
    }
}
