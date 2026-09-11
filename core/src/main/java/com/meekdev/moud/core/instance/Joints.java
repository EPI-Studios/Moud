package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;

// the one writer of a limb's frame
//
// nothing else may write a part that a joint drives. that is the whole point: before this, a pose
// and the rig both wrote the same property from opposite ends, and whichever went last won. now
// the rig owns where a joint stands, a pose owns the turn at it, and this composes the two into
// the frame the renderer reads
public final class Joints {

    private static final PropertyDef CFRAME = Classes.PART.property("cframe");

    private Joints() {}

    public static void follow(InstanceTree tree) {
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character) apply(character);
        }
    }

    public static void apply(Character character) {
        if (!(character.child(Rig.JOINTS) instanceof Instance joints)) return;

        // the body's own joint first: every limb hangs through it, which is how a body lies down
        // dead or goes flat in the water without any limb knowing it did
        CFrame root = joints.child(Rig.ROOT) instanceof Joint hinge ? hinge.transform : CFrame.IDENTITY;

        for (Instance instance : joints.children()) {
            if (!(instance instanceof Joint joint)) continue;
            if (!(joint.part1 instanceof Part part) || !part.isAlive()) continue;
            Instances.setObj(part, CFRAME, root.mul(joint.c0).mul(joint.transform).mul(inverse(joint.c1)));
        }
    }

    // c1 is almost always nothing, so this almost always costs a comparison
    private static CFrame inverse(CFrame frame) {
        return frame.equals(CFrame.IDENTITY) ? CFrame.IDENTITY : frame.inverse();
    }
}
