package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JointPose(Vector3 rotation, Vector3 position, Vector3 scale) {

    public static final JointPose REST = new JointPose(Vector3.ZERO, Vector3.ZERO, Vector3.ONE);

    public CFrame transform() {
        return new CFrame(position, Euler.toQuat(rotation));
    }

    public Vector3 value(Channel channel) {
        return switch (channel) {
            case ROTATION -> rotation;
            case POSITION -> position;
            case SCALE -> scale;
        };
    }

    public static JointPose of(AnimClip clip, String joint, double time) {
        return new JointPose(
                Curves.sample(clip.keys(joint, Channel.ROTATION), time, Channel.ROTATION.rest()),
                Curves.sample(clip.keys(joint, Channel.POSITION), time, Channel.POSITION.rest()),
                Curves.sample(clip.keys(joint, Channel.SCALE), time, Channel.SCALE.rest()));
    }

    public static Map<String, JointPose> all(AnimClip clip, List<String> joints, double time) {
        Map<String, JointPose> poses = new LinkedHashMap<>();
        for (String joint : joints) poses.put(joint, of(clip, joint, time));
        return poses;
    }

    public boolean near(JointPose other, double tolerance) {
        return rotation.sub(other.rotation).length() < tolerance && position.sub(other.position).length() * 16 < tolerance
                && scale.sub(other.scale).length() * 16 < tolerance;
    }
}
