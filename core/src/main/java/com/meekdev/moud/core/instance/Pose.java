package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

// how a body stands, ported from the game's own humanoid animation
//
// the constants are its constants: limbs swing on a cosine of distance walked, arms at twice the
// amplitude of legs and half the gain, the two sides half a cycle apart, and a crouch leans the
// torso while dropping everything hung off it. a walk cycle that is merely similar reads as wrong
// immediately, which is why these are copied rather than tuned
//
// the model turns the opposite way round from us on two axes, because the whole thing is drawn
// through half a turn about z. that conversion happens in turn(), once
public final class Pose {

    private static final double SWING = 0.6662;

    private static final PropertyDef CFRAME = Classes.PART.property("cframe");

    private Pose() {}

    public static void apply(Character character) {
        double phase = character.moveDistance * SWING;
        double gain = character.moveSpeed;

        // the arms lead the legs by half a cycle, which is what makes a walk look like a walk
        double rightArm = Math.cos(phase + Math.PI) * 2.0 * gain * 0.5;
        double leftArm = Math.cos(phase) * 2.0 * gain * 0.5;
        double rightLeg = Math.cos(phase) * 1.4 * gain;
        double leftLeg = Math.cos(phase + Math.PI) * 1.4 * gain;

        double lean = 0;
        double armCrouch = 0;
        if (character.crouching) {
            lean = 0.5;
            armCrouch = 0.4;
        }

        turn(character, "head", character.lookPitch, character.lookYaw, 0);
        turn(character, "torso", lean, 0, 0);
        turn(character, "rightArm", rightArm + armCrouch, 0, 0);
        turn(character, "leftArm", leftArm + armCrouch, 0, 0);
        // the legs are given a hair of yaw and roll so the two never coplanar z fight
        turn(character, "rightLeg", rightLeg, 0.005, 0.005);
        turn(character, "leftLeg", leftLeg, -0.005, -0.005);
    }

    // an angle the model states is the opposite of the one we turn by on x and y, and the same
    // on z, because the model is drawn through half a turn about z
    private static void turn(Character character, String name, double x, double y, double z) {
        if (!(character.child(name) instanceof Part part)) return;
        Quat rotation = Quat.axisAngle(Vec3.UP, -y)
                .mul(Quat.axisAngle(new Vec3(0, 0, 1), z))
                .mul(Quat.axisAngle(new Vec3(1, 0, 0), -x));
        Instances.setObj(part, CFRAME, new CFrame(part.cframe.position(), rotation));
    }
}
