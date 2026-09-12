package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

// how a body stands, ported from the game's own humanoid animation
//
// the constants are its constants and the order is its order: walk, riding, the swing, the crouch,
// the idle sway, then swimming over the top. the order is not decoration -- each stage reads what
// the one before it left, so a crouched swing and a swinging crouch are different poses, and
// moving one stage past another quietly changes both
//
// every limb is carried here as the model carries it: three angles and a joint offset, in the
// model's own units and its own directions, converted once at the end in turn(). that is why a
// number lifted out of the decompiler can be pasted in and be right
//
// the model turns the opposite way round from us on two axes, because the whole thing is drawn
// through half a turn about z
public final class Pose {

    private static final double SWING = 0.6662;

    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    // the six parts are siblings, exactly as the model has them: nothing hangs off the torso, so
    // a torso that twists has to hand its twist to the arms by hand. the game does the same
    private static final PropertyDef TRANSFORM = Classes.JOINT.property("transform");

    // one limb, as the model states it
    private static final class Limb {

        double x;
        double y;
        double z;

        // where the joint moved to, from where it stands, in model units
        double atX;
        double atY;
        double atZ;
    }

    private Pose() {}

    // ageInTicks is the body's own age, which is what the idle sway runs on. two bodies standing
    // side by side sway out of phase because they are not the same age
    public static void apply(Character character, double ageInTicks) {
        double phase = character.moveDistance * SWING;
        double gain = character.moveSpeed;

        Limb head = new Limb();
        Limb torso = new Limb();
        Limb rightArm = new Limb();
        Limb leftArm = new Limb();
        Limb rightLeg = new Limb();
        Limb leftLeg = new Limb();

        head.x = character.lookPitch;
        head.y = character.lookYaw;
        if (character.flying) {
            head.x = -Math.PI / 4;
        } else if (character.swimAmount > 0) {
            head.x = rotLerp(character.swimAmount, head.x, -Math.PI / 4);
        }

        // the arms lead the legs by half a cycle, which is what makes a walk look like a walk.
        // all four are divided by the stride, exactly as the model divides them
        double stride = character.speedValue;
        rightArm.x = Math.cos(phase + Math.PI) * 2.0 * gain * 0.5 / stride;
        leftArm.x = Math.cos(phase) * 2.0 * gain * 0.5 / stride;
        rightLeg.x = Math.cos(phase) * 1.4 * gain / stride;
        leftLeg.x = Math.cos(phase + Math.PI) * 1.4 * gain / stride;
        // a hair of yaw and roll so the two legs are never coplanar and never z fight
        rightLeg.y = 0.005;
        rightLeg.z = 0.005;
        leftLeg.y = -0.005;
        leftLeg.z = -0.005;

        if (character.riding) {
            rightArm.x += -Math.PI / 5;
            leftArm.x += -Math.PI / 5;
            // set, not added: a sat body folds its legs wherever the walk had left them
            rightLeg.x = -1.4137167;
            rightLeg.y = Math.PI / 10;
            rightLeg.z = 0.07853982;
            leftLeg.x = -1.4137167;
            leftLeg.y = -Math.PI / 10;
            leftLeg.z = -0.07853982;
        }

        arms(character, head, rightArm, leftArm);
        swing(character, head, torso, rightArm, leftArm);

        if (character.crouching) {
            torso.x = 0.5;
            rightArm.x += 0.4;
            leftArm.x += 0.4;
            // a crouch is not only a lean: it drops everything hung off the torso and sits the
            // legs back under it. leaning alone reads as bowing
            rightLeg.atZ += 4.0;
            leftLeg.atZ += 4.0;
            head.atY += 4.2;
            torso.atY += 3.2;
            rightArm.atY += 3.2;
            leftArm.atY += 3.2;
        }

        // the sway an idle body carries, one arm against the other. without it a body standing
        // still is perfectly rigid, which is the clearest tell that a model is not the game's own
        double swayZ = Math.cos(ageInTicks * 0.09) * 0.05 + 0.05;
        double swayX = Math.sin(ageInTicks * 0.067) * 0.05;
        rightArm.z += swayZ;
        rightArm.x += swayX;
        leftArm.z -= swayZ;
        leftArm.x -= swayX;

        swim(character, rightArm, leftArm, rightLeg, leftLeg);

        double scale = character.scale;
        // the tilt goes on the body's own joint, so every limb inherits it through one write
        // rather than six, and a place can read what tilted the body
        if (Rig.joint(character, Rig.ROOT) instanceof Joint hinge) {
            Instances.setObj(hinge, TRANSFORM, root(character, ageInTicks));
        }
        turn(character, "head", head, scale);
        turn(character, "torso", torso, scale);
        turn(character, "rightArm", rightArm, scale);
        turn(character, "leftArm", leftArm, scale);
        turn(character, "rightLeg", rightLeg, scale);
        turn(character, "leftLeg", leftLeg, scale);
        wings(character, scale);
        cape(character);
        // the tracks a place is playing go over what this left, and only then do the joints
        // settle. a track that names a joint takes it; one that does not, leaves it walking
        Animators.apply(character);
        // a pose that has been applied leaves the body in it, rather than leaving six joints
        // written and the body still standing where it was
        Joints.apply(character);
    }

    // how the whole body is hung, before any limb is posed
    //
    // this is the model's setupRotations, minus the one rotation we already carry: the body's own
    // yaw is on the character's frame, where the hitbox and everything a place hangs off the body
    // can see it. what is left is the tilting -- and it goes on the six limbs rather than on the
    // character, because a body lying dead or flat in the water still collides standing up
    //
    private static CFrame root(Character character, double ageInTicks) {
        Quat r = Quat.IDENTITY;
        Vec3 at = Vec3.ZERO;
        double pitch = Math.toDegrees(character.lookPitch);

        if (character.frozen) {
            // the model adds this to the body yaw and then turns by a half turn minus it, so
            // against a yaw we have already applied it comes back the other way round
            double shake = Math.cos(Math.floor(ageInTicks) * 3.25) * Math.PI * 0.4;
            r = r.mul(spin(Vec3.UP, -shake));
        }

        if (character.deathTime > 0) {
            // twenty ticks from upright to flat, on a square root so it drops fast and settles
            double fall = Math.min(1.0, Math.sqrt(
                    Math.max(0, (character.deathTime - 1.0) / 20.0 * 1.6)));
            r = r.mul(spin(FORWARD, fall * 90.0));
        } else if (character.spinning) {
            r = r.mul(spin(RIGHT, -90.0 - pitch)).mul(spin(Vec3.UP, ageInTicks * -75.0));
        } else if (character.sleeping) {
            // the heading is already on the character's frame, put there as the bed's rather than
            // the body's -- which is what the model does by skipping its own yaw here. what is
            // left is laying the body over and turning it to face along the bed
            r = r.mul(spin(FORWARD, 90.0)).mul(spin(Vec3.UP, 270.0));
        } else if (character.upsideDown) {
            at = new Vec3(0, character.height + 0.1, 0);
            r = r.mul(spin(FORWARD, 180.0));
        }

        if (character.flying) {
            // the tilt arrives over the first ten ticks under the wing rather than at once
            double onset = Math.min(1.0, character.flyingTime * character.flyingTime / 100.0);
            if (!character.spinning) r = r.mul(spin(RIGHT, onset * (-90.0 - pitch)));
            r = r.mul(Quat.axisAngle(Vec3.UP, character.flyingYaw));
        } else if (character.swimAmount > 0) {
            // in water the body follows its own look, out of it the crawl is flat
            double target = character.inWater ? -90.0 - pitch : -90.0;
            r = r.mul(spin(RIGHT, character.swimAmount * target));
            if (character.crawling) at = at.add(new Vec3(0, -1, 0.3));
        }
        return new CFrame(at, r);
    }

    private static Quat spin(Vec3 axis, double degrees) {
        return Quat.axisAngle(axis, Math.toRadians(degrees));
    }


    // which arm is posed first, and whether the other one gets its own pose at all
    //
    // this order is the model's and it is not decoration: a two handed pose writes both arms, so
    // posing the other one afterwards would undo half of it. the used hand goes first when a use
    // is held, and the main hand goes first otherwise unless the off hand is the two handed one
    private static void arms(Character character, Limb head, Limb rightArm, Limb leftArm) {
        boolean rightHanded = !character.mainLeft;
        boolean first;
        if (character.usingItem) {
            first = (!character.useLeftHand) == rightHanded;
        } else {
            boolean twoHandedOffhand = rightHanded
                    ? character.leftArmPose.twoHanded()
                    : character.rightArmPose.twoHanded();
            first = rightHanded == twoHandedOffhand;
        }

        if (first) {
            poseRight(character, head, rightArm, leftArm);
            if (!character.rightArmPose.affectsOther()) poseLeft(character, head, rightArm, leftArm);
        } else {
            poseLeft(character, head, rightArm, leftArm);
            if (!character.leftArmPose.affectsOther()) poseRight(character, head, rightArm, leftArm);
        }
    }

    private static void poseRight(Character character, Limb head, Limb rightArm, Limb leftArm) {
        switch (character.rightArmPose) {
            case EMPTY -> rightArm.y = 0;
            case BLOCK -> block(head, rightArm, true);
            case ITEM -> {
                rightArm.x = rightArm.x * 0.5 - 0.31415927;
                rightArm.y = 0;
            }
            case TRIDENT -> {
                rightArm.x = rightArm.x * 0.5 - 3.1415927;
                rightArm.y = 0;
            }
            case BOW -> {
                rightArm.y = -0.1 + head.y;
                leftArm.y = 0.1 + head.y + 0.4;
                rightArm.x = -1.5707964 + head.x;
                leftArm.x = -1.5707964 + head.x;
            }
            case CROSSBOW_CHARGE -> charge(character, rightArm, leftArm, true);
            case CROSSBOW_HOLD -> hold(head, rightArm, leftArm, true);
            case SPYGLASS -> {
                rightArm.x = clamp(head.x - 1.9198622
                        - (character.crouching ? 0.2617994 : 0), -2.4, 3.3);
                rightArm.y = head.y - 0.2617994;
            }
            case HORN -> {
                rightArm.x = clamp(head.x, -1.2, 1.2) - 1.4835298;
                rightArm.y = head.y - 0.5235988;
            }
            case BRUSH -> {
                rightArm.x = rightArm.x * 0.5 - 0.62831855;
                rightArm.y = 0;
            }
        }
    }

    private static void poseLeft(Character character, Limb head, Limb rightArm, Limb leftArm) {
        switch (character.leftArmPose) {
            case EMPTY -> leftArm.y = 0;
            case BLOCK -> block(head, leftArm, false);
            case ITEM -> {
                leftArm.x = leftArm.x * 0.5 - 0.31415927;
                leftArm.y = 0;
            }
            case TRIDENT -> {
                leftArm.x = leftArm.x * 0.5 - 3.1415927;
                leftArm.y = 0;
            }
            case BOW -> {
                rightArm.y = -0.1 + head.y - 0.4;
                leftArm.y = 0.1 + head.y;
                rightArm.x = -1.5707964 + head.x;
                leftArm.x = -1.5707964 + head.x;
            }
            case CROSSBOW_CHARGE -> charge(character, rightArm, leftArm, false);
            case CROSSBOW_HOLD -> hold(head, rightArm, leftArm, false);
            case SPYGLASS -> {
                leftArm.x = clamp(head.x - 1.9198622
                        - (character.crouching ? 0.2617994 : 0), -2.4, 3.3);
                leftArm.y = head.y + 0.2617994;
            }
            case HORN -> {
                leftArm.x = clamp(head.x, -1.2, 1.2) - 1.4835298;
                leftArm.y = head.y + 0.5235988;
            }
            case BRUSH -> {
                leftArm.x = leftArm.x * 0.5 - 0.62831855;
                leftArm.y = 0;
            }
        }
    }

    // a shield goes up in front of the face and follows it, within limits: the head can look past
    // the shield without dragging it off the body
    private static void block(Limb head, Limb arm, boolean right) {
        arm.x = arm.x * 0.5 - 0.9424779 + clamp(head.x, -Math.PI * 4.0 / 9.0, 0.43633232);
        arm.y = (right ? -30.0 : 30.0) * (Math.PI / 180.0)
                + clamp(head.y, -Math.PI / 6, Math.PI / 6);
    }

    // winding a crossbow: the holding arm is still and the pulling one comes across as it winds
    private static void charge(Character character, Limb rightArm, Limb leftArm, boolean right) {
        Limb holding = right ? rightArm : leftArm;
        Limb pulling = right ? leftArm : rightArm;
        holding.y = right ? -0.8 : 0.8;
        holding.x = -0.97079635;
        pulling.x = holding.x;
        double wound = character.chargeProgress;
        pulling.y = lerp(wound, 0.4, 0.85) * (right ? 1 : -1);
        pulling.x = lerp(wound, pulling.x, -Math.PI / 2);
    }

    private static void hold(Limb head, Limb rightArm, Limb leftArm, boolean right) {
        Limb holding = right ? rightArm : leftArm;
        Limb shooting = right ? leftArm : rightArm;
        holding.y = (right ? -0.3 : 0.3) + head.y;
        shooting.y = (right ? 0.6 : -0.6) + head.y;
        holding.x = -Math.PI / 2 + head.x + 0.1;
        shooting.x = -1.5 + head.x;
    }

    private static double clamp(double value, double low, double high) {
        return value < low ? low : Math.min(value, high);
    }

    // the attack, which twists the whole torso and carries the shoulders round with it
    //
    // the arms are siblings of the torso, so they do not inherit the twist: the model adds it to
    // their yaw and walks their joints round the turn by hand, which is why this moves joints at
    // all. a swing that only rotates the arm is the one that looks like a puppet
    private static void swing(Character character, Limb head, Limb torso,
                              Limb rightArm, Limb leftArm) {
        double attack = character.attackTime;
        if (attack <= 0) return;

        double twist = Math.sin(Math.sqrt(attack) * Math.PI * 2) * 0.2;
        if (character.attackLeft) twist = -twist;
        torso.y = twist;

        // the shoulder line turns with the torso: the joint that stood five out to the side ends
        // up five out along the turn instead, so the offset is the difference between the two
        rightArm.atX += 5.0 - Math.cos(twist) * 5.0;
        rightArm.atZ += Math.sin(twist) * 5.0;
        leftArm.atX += Math.cos(twist) * 5.0 - 5.0;
        leftArm.atZ += -Math.sin(twist) * 5.0;

        rightArm.y += twist;
        leftArm.y += twist;
        leftArm.x += twist;

        double eased = 1.0 - square(square(1.0 - attack));
        double reach = Math.sin(eased * Math.PI);
        // the swing is aimed where the head looks, so looking up throws the arm further back
        double aim = Math.sin(attack * Math.PI) * -(head.x - 0.7) * 0.75;

        Limb arm = character.attackLeft ? leftArm : rightArm;
        arm.x -= reach * 1.2 + aim;
        arm.y += twist * 2.0;
        arm.z += Math.sin(attack * Math.PI) * -0.4;
    }

    // the crawl, on its own twenty six unit cycle, blended over whatever the walk left
    //
    // the two arms are blended with different functions in the model -- the left wraps its angles
    // and the right does not. that is copied rather than tidied: tidying it changes the pose
    private static void swim(Character character, Limb rightArm, Limb leftArm,
                             Limb rightLeg, Limb leftLeg) {
        double amount = character.swimAmount;
        if (amount <= 0) return;

        double pos = character.moveDistance % 26.0;
        if (pos < 14.0) {
            leftArm.x = rotLerp(amount, leftArm.x, 0);
            rightArm.x = lerp(amount, rightArm.x, 0);
            leftArm.y = rotLerp(amount, leftArm.y, Math.PI);
            rightArm.y = lerp(amount, rightArm.y, Math.PI);
            leftArm.z = rotLerp(amount, leftArm.z,
                    Math.PI + 1.8707964 * reach(pos) / reach(14.0));
            rightArm.z = lerp(amount, rightArm.z,
                    Math.PI - 1.8707964 * reach(pos) / reach(14.0));
        } else if (pos < 22.0) {
            double through = (pos - 14.0) / 8.0;
            leftArm.x = rotLerp(amount, leftArm.x, Math.PI / 2 * through);
            rightArm.x = lerp(amount, rightArm.x, Math.PI / 2 * through);
            leftArm.y = rotLerp(amount, leftArm.y, Math.PI);
            rightArm.y = lerp(amount, rightArm.y, Math.PI);
            leftArm.z = rotLerp(amount, leftArm.z, 5.012389 - 1.8707964 * through);
            rightArm.z = lerp(amount, rightArm.z, 1.2707963 + 1.8707964 * through);
        } else {
            double through = (pos - 22.0) / 4.0;
            leftArm.x = rotLerp(amount, leftArm.x, Math.PI / 2 - Math.PI / 2 * through);
            rightArm.x = lerp(amount, rightArm.x, Math.PI / 2 - Math.PI / 2 * through);
            leftArm.y = rotLerp(amount, leftArm.y, Math.PI);
            rightArm.y = lerp(amount, rightArm.y, Math.PI);
            leftArm.z = rotLerp(amount, leftArm.z, Math.PI);
            rightArm.z = lerp(amount, rightArm.z, Math.PI);
        }

        double kick = character.moveDistance * 0.33333334;
        leftLeg.x = lerp(amount, leftLeg.x, 0.3 * Math.cos(kick + Math.PI));
        rightLeg.x = lerp(amount, rightLeg.x, 0.3 * Math.cos(kick));
    }

    // the model's own name for it, and its own curve: how far through the stroke an arm is
    private static double reach(double at) {
        return -65.0 * at + at * at;
    }

    // an angle the model states is the opposite of the one we turn by on x and y, and the same
    // on z, because the model is drawn through half a turn about z
    //
    // the joint always starts from where the rig says it stands, never from where the last tick
    // left it: the model states every one of these as an offset on the standing pose, and reading
    // back the offset one would compound it every tick until the body came apart
    // the pair on the back
    //
    // one wing is stated and the other is its mirror on two axes out of three -- they hinge apart
    // rather than turning together, which is the whole shape of an elytra opening. crouching
    // drops them three texels so they clear the folded body
    private static void wings(Character character, double scale) {
        Wings pair = Rig.wings(character);
        if (pair == null || !pair.worn) return;
        Limb right = new Limb();
        Limb left = new Limb();

        left.x = pair.x;
        left.y = pair.y;
        left.z = pair.z;
        right.x = pair.x;
        right.y = -pair.y;
        right.z = -pair.z;

        if (character.crouching) {
            left.atY += 3.0;
            right.atY += 3.0;
        }
        turn(character, "rightWing", right, scale);
        turn(character, "leftWing", left, scale);
    }

    // how a cape hangs
    //
    // four turns in a row, and the first of them undoes the half turn the model hangs it at. the
    // two lean terms are halved because each is applied twice -- once as a tilt and once as the
    // yaw that follows it round, which is what makes a cape trail behind a turn instead of
    // swinging flat
    private static void cape(Character character) {
        if (!(Rig.joint(character, Rig.CAPE) instanceof Joint hinge)) return;
        if (!(hinge.part1 instanceof Cape cape) || !cape.visible) return;

        double tilt = Math.toRadians(6.0 + cape.lean / 2.0 + cape.flap);
        double roll = Math.toRadians(cape.sway / 2.0);
        double swing = Math.toRadians(180.0 - cape.sway / 2.0);

        Quat rotation = Quat.axisAngle(Vec3.UP, Math.PI)
                .mul(Quat.axisAngle(RIGHT, -tilt))
                .mul(Quat.axisAngle(FORWARD, roll))
                .mul(Quat.axisAngle(Vec3.UP, -swing));
        Instances.setObj(hinge, TRANSFORM, new CFrame(Vec3.ZERO, rotation));
    }

    // the turn at a joint, and nothing else. where the joint stands is the rig's and is not
    // touched here, which is why a pose can no longer lose one
    private static void turn(Character character, String name, Limb limb, double scale) {
        if (!(Rig.joint(character, name) instanceof Joint hinge)) return;
        Quat rotation = Quat.axisAngle(Vec3.UP, -limb.y)
                .mul(Quat.axisAngle(FORWARD, limb.z))
                .mul(Quat.axisAngle(RIGHT, -limb.x));
        Vec3 at = Rig.offset(limb.atX, limb.atY, limb.atZ).mul(scale);
        Instances.setObj(hinge, TRANSFORM, new CFrame(at, rotation));
    }

    private static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    // the same, on the short way round a circle, so a blend from just under a half turn to just
    // over it does not unwind the whole way back
    private static double rotLerp(double t, double from, double to) {
        double difference = (to - from) % (Math.PI * 2);
        if (difference >= Math.PI) difference -= Math.PI * 2;
        if (difference < -Math.PI) difference += Math.PI * 2;
        return from + difference * t;
    }

    private static double square(double x) {
        return x * x;
    }
}
