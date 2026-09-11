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
// the order is the model's order too: walk, then crouch, then the idle sway last over the top.
// swapping the last two changes what a crouched body looks like, because the sway is added to
// whatever the crouch left rather than to the standing pose
//
// the model turns the opposite way round from us on two axes, because the whole thing is drawn
// through half a turn about z. that conversion happens in turn(), once
public final class Pose {

    private static final double SWING = 0.6662;

    private static final PropertyDef CFRAME = Classes.PART.property("cframe");

    private Pose() {}

    // ageInTicks is the body's own age, which is what the idle sway runs on. two bodies standing
    // side by side sway out of phase because they are not the same age, and passing one clock for
    // everybody would have them breathe in unison
    public static void apply(Character character, double ageInTicks) {
        double phase = character.moveDistance * SWING;
        double gain = character.moveSpeed;
        double scale = character.scale;

        // the arms lead the legs by half a cycle, which is what makes a walk look like a walk
        double rightArmX = Math.cos(phase + Math.PI) * 2.0 * gain * 0.5;
        double leftArmX = Math.cos(phase) * 2.0 * gain * 0.5;
        double rightLegX = Math.cos(phase) * 1.4 * gain;
        double leftLegX = Math.cos(phase + Math.PI) * 1.4 * gain;

        double torsoX = 0;
        Vec3 headAt = Vec3.ZERO;
        Vec3 torsoAt = Vec3.ZERO;
        Vec3 armAt = Vec3.ZERO;
        Vec3 legAt = Vec3.ZERO;

        if (character.crouching) {
            torsoX = 0.5;
            rightArmX += 0.4;
            leftArmX += 0.4;
            // a crouch is not only a lean: the model drops everything hung off the torso and sits
            // the legs back under it. leaning alone leaves the head where a standing one was and
            // the body reads as bowing rather than as crouching
            headAt = Rig.offset(0, 4.2, 0).mul(scale);
            torsoAt = Rig.offset(0, 3.2, 0).mul(scale);
            armAt = Rig.offset(0, 3.2, 0).mul(scale);
            legAt = Rig.offset(0, 0, 4.0).mul(scale);
        }

        // the sway an idle body carries, one arm against the other. without it a body standing
        // still is perfectly rigid, which is the single clearest tell that a model is not the
        // game's own
        double swayZ = Math.cos(ageInTicks * 0.09) * 0.05 + 0.05;
        double swayX = Math.sin(ageInTicks * 0.067) * 0.05;

        turn(character, "head", character.lookPitch, character.lookYaw, 0, headAt);
        turn(character, "torso", torsoX, 0, 0, torsoAt);
        turn(character, "rightArm", rightArmX + swayX, 0, swayZ, armAt);
        turn(character, "leftArm", leftArmX - swayX, 0, -swayZ, armAt);
        // the legs are given a hair of yaw and roll so the two never coplanar z fight
        turn(character, "rightLeg", rightLegX, 0.005, 0.005, legAt);
        turn(character, "leftLeg", leftLegX, -0.005, -0.005, legAt);
    }

    // an angle the model states is the opposite of the one we turn by on x and y, and the same
    // on z, because the model is drawn through half a turn about z
    private static void turn(Character character, String name, double x, double y, double z,
                             Vec3 offset) {
        if (!(character.child(name) instanceof Part part)) return;
        Quat rotation = Quat.axisAngle(Vec3.UP, -y)
                .mul(Quat.axisAngle(new Vec3(0, 0, 1), z))
                .mul(Quat.axisAngle(new Vec3(1, 0, 0), -x));
        Vec3 at = Rig.pivot(name, character.scale).add(offset);
        Instances.setObj(part, CFRAME, new CFrame(at, rotation));
    }
}
