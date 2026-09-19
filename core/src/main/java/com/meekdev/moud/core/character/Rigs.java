package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Rigs {

    private static final PropertyDef JOINT_TRANSFORM = Classes.JOINT.property("transform");
    private static final PropertyDef JOINT_SCALE = Classes.JOINT.property("scale");
    private static final PropertyDef BONE_TRANSFORM = Classes.BONE.property("transform");
    private static final PropertyDef BONE_SCALE = Classes.BONE.property("scale");

    private static final int DEEPEST = 64;

    private Rigs() {}

    public static boolean posable(Instance instance) {
        return instance instanceof Joint || instance instanceof Bone;
    }

    public static Map<String, Instance> joints(Animator animator) {
        Map<String, Instance> joints = new LinkedHashMap<>();
        Instance holder = animator.parent();
        if (holder instanceof Character character) {
            if (character.child(Rig.JOINTS) instanceof Instance folder) {
                for (Instance child : folder.children()) {
                    if (child instanceof Joint) joints.putIfAbsent(child.name(), child);
                }
            }
            return joints;
        }
        if (holder instanceof ViewModel) gather(holder, joints);
        if (holder instanceof AnimationController && holder.parent() != null) gather(holder.parent(), joints);
        return joints;
    }

    private static void gather(Instance under, Map<String, Instance> joints) {
        for (Instance child : under.children()) {
            if (child instanceof Character) continue;
            if (posable(child)) joints.putIfAbsent(child.name(), child);
            gather(child, joints);
        }
    }

    public static CFrame transform(Instance joint) {
        return switch (joint) {
            case Bone bone -> bone.transform;
            case Joint hinge -> hinge.transform;
            default -> CFrame.IDENTITY;
        };
    }

    public static void transform(Instance joint, CFrame value) {
        if (transform(joint).equals(value)) return;
        Instances.setObj(joint, joint instanceof Bone ? BONE_TRANSFORM : JOINT_TRANSFORM, value);
    }

    public static Vector3 scale(Instance joint) {
        return switch (joint) {
            case Bone bone -> bone.scale;
            case Joint hinge -> hinge.scale;
            default -> Vector3.ONE;
        };
    }

    public static void scale(Instance joint, Vector3 value) {
        if (scale(joint).equals(value)) return;
        Instances.setObj(joint, joint instanceof Bone ? BONE_SCALE : JOINT_SCALE, value);
    }

    public static CFrame parentFrame(Instance joint) {
        return parentFrame(joint, 0);
    }

    private static CFrame parentFrame(Instance joint, int depth) {
        if (joint instanceof Bone bone) {
            Instance above = bone.parent();
            return (above == null ? CFrame.IDENTITY : Transforms.world(above)).mul(bone.cframe);
        }
        if (joint instanceof Joint hinge) return partWorld(hinge, hinge.part0, depth).mul(hinge.c0);
        return Transforms.world(joint);
    }

    public static CFrame frame(Instance joint) {
        return frame(joint, 0);
    }

    private static CFrame frame(Instance joint, int depth) {
        return parentFrame(joint, depth).mul(transform(joint));
    }

    private static CFrame partWorld(Joint joint, Instance part, int depth) {
        if (part == null) return CFrame.IDENTITY;
        Joint holding = holding(joint, part);
        if (holding != null && depth < DEEPEST) return frame(holding, depth + 1).mul(holding.c1.inverse());
        return Transforms.world(part);
    }

    private static Joint holding(Joint joint, Instance part) {
        Instance folder = joint.parent();
        if (folder == null) return null;
        for (Instance sibling : folder.children()) {
            if (sibling != joint && sibling instanceof Joint other && other.part1 == part) return other;
        }
        return null;
    }

    public static Instance rigParent(Instance joint) {
        if (joint instanceof Bone bone) return bone.parent() instanceof Bone above ? above : null;
        if (joint instanceof Joint hinge) return holding(hinge, hinge.part0);
        return null;
    }

    public static CFrame world(Instance target) {
        if (posable(target)) return frame(target);
        if (target instanceof Spatial) return Transforms.world(target);
        return null;
    }

    public static void turnTo(Instance joint, Quat world) {
        Quat local = parentFrame(joint).rotation().inverse().mul(world).normalize();
        transform(joint, transform(joint).withRotation(local));
    }
}
