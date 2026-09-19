package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JointPose(Vector3 rotation, Vector3 position, Vector3 scale, CFrame transform) {

    public static final JointPose REST = new JointPose(Vector3.ZERO, Vector3.ZERO, Vector3.ONE, CFrame.IDENTITY);

    public Vector3 value(Channel channel) {
        return switch (channel) {
            case ROTATION -> rotation;
            case POSITION -> position;
            case SCALE -> scale;
        };
    }

    public static JointPose of(AnimClip clip, String channel, double time) {
        Vector3 rotation = Curves.sample(clip.keys(channel, Channel.ROTATION), time, Channel.ROTATION.rest());
        Vector3 position = Curves.sample(clip.keys(channel, Channel.POSITION), time, Channel.POSITION.rest());
        Vector3 scale = Curves.sample(clip.keys(channel, Channel.SCALE), time, Channel.SCALE.rest());
        return new JointPose(rotation, position, scale, new CFrame(position, Clip.euler(rotation, clip.euler)));
    }

    public static Map<String, JointPose> all(AnimClip clip, Clip compiled, List<String> joints, double time, boolean retarget) {
        Map<String, Clip.Sample> composed = retarget && !compiled.skeleton().isEmpty() ? compiled.composed(time) : Map.of();
        Map<String, JointPose> poses = new LinkedHashMap<>();
        for (String joint : joints) {
            String channel = compiled.channelFor(joint, retarget);
            Clip.Sample sample = channel == null ? null : compiled.sample(joint, time, retarget, composed);
            if (sample == null) {
                poses.put(joint, REST);
                continue;
            }
            JointPose raw = of(clip, channel, time);
            CFrame transform = new CFrame(sample.position() == null ? Vector3.ZERO : sample.position(),
                    sample.rotation() == null ? Quat.IDENTITY : sample.rotation());
            poses.put(joint, new JointPose(raw.rotation(), raw.position(), sample.scale() == null ? Vector3.ONE : sample.scale(), transform));
        }
        return poses;
    }

    public boolean near(JointPose other, double tolerance) {
        return rotation.sub(other.rotation).length() < tolerance && position.sub(other.position).length() * 16 < tolerance
                && scale.sub(other.scale).length() * 16 < tolerance;
    }
}
