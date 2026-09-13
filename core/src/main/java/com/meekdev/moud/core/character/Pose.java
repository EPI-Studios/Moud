package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.LinkedHashMap;
import java.util.Map;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Joint;

public final class Pose {

    private static final double SWING = 0.6662;

    private static final Vector3 RIGHT = new Vector3(1, 0, 0);
    private static final Vector3 FORWARD = new Vector3(0, 0, 1);

    private static final PropertyDef TRANSFORM = Classes.JOINT.property("transform");

    private static final class Turn {

        double x;
        double y;
        double z;

        double atX;
        double atY;
        double atZ;
    }

    private Pose() {}

    public static void apply(Character character, double ageInTicks) {
        double phase = character.moveDistance * SWING;
        double gain = character.moveSpeed;

        Map<String, Turn> turns = new LinkedHashMap<>();
        walk(character, turns, phase, gain);

        Turn head = turnFor(turns, "head");
        Turn torso = turnFor(turns, "torso");
        Turn rightArm = turnFor(turns, "rightArm");
        Turn leftArm = turnFor(turns, "leftArm");
        Turn rightLeg = turnFor(turns, "rightLeg");
        Turn leftLeg = turnFor(turns, "leftLeg");

        head.x = character.lookPitch;
        head.y = character.lookYaw;
        if (character.flying) {
            head.x = -Math.PI / 4;
        } else if (character.swimAmount > 0) {
            head.x = rotLerp(character.swimAmount, head.x, -Math.PI / 4);
        }

        rightLeg.y = 0.005;
        rightLeg.z = 0.005;
        leftLeg.y = -0.005;
        leftLeg.z = -0.005;

        if (character.riding) {
            rightArm.x += -Math.PI / 5;
            leftArm.x += -Math.PI / 5;
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
            rightLeg.atZ += 4.0;
            leftLeg.atZ += 4.0;
            head.atY += 4.2;
            torso.atY += 3.2;
            rightArm.atY += 3.2;
            leftArm.atY += 3.2;
        }

        double swayZ = Math.cos(ageInTicks * 0.09) * 0.05 + 0.05;
        double swayX = Math.sin(ageInTicks * 0.067) * 0.05;
        rightArm.z += swayZ;
        rightArm.x += swayX;
        leftArm.z -= swayZ;
        leftArm.x -= swayX;

        swim(character, rightArm, leftArm, rightLeg, leftLeg);

        double scale = character.scale;
        if (Rig.joint(character, Rig.ROOT) instanceof Joint hinge) {
            Instances.setObj(hinge, TRANSFORM, root(character, ageInTicks));
        }
        for (Map.Entry<String, Turn> one : turns.entrySet()) {
            turn(character, one.getKey(), one.getValue(), scale);
        }
        wings(character, scale);
        cape(character);
        spin(character, ageInTicks, scale);
        Animators.apply(character);
    }

    private static void walk(Character character, Map<String, Turn> turns, double phase,
                             double gain) {
        double stride = character.speedValue;
        if (!(character.child(Rig.JOINTS) instanceof Instance joints)) return;
        for (Instance child : joints.children()) {
            if (!(child instanceof Joint hinge) || !(hinge.part1 instanceof Limb limb)) continue;
            if (limb.swing == 0) continue;
            Turn turn = turnFor(turns, child.name());
            double swung = Math.cos(phase + limb.swingPhase * Math.PI * 2)
                    * limb.swing * gain / stride;
            if (limb.swingSideways) {
                turn.y += swung;
            } else {
                turn.x += swung;
            }
        }
    }

    private static Turn turnFor(Map<String, Turn> turns, String joint) {
        return turns.computeIfAbsent(joint, name -> new Turn());
    }

    private static CFrame root(Character character, double ageInTicks) {
        Quat r = Quat.IDENTITY;
        Vector3 at = Vector3.ZERO;
        double pitch = Math.toDegrees(character.lookPitch);

        if (character.frozen) {
            double shake = Math.cos(Math.floor(ageInTicks) * 3.25) * Math.PI * 0.4;
            r = r.mul(spin(Vector3.UP, -shake));
        }

        if (character.deathTime > 0) {
            double fall = Math.min(1.0, Math.sqrt(
                    Math.max(0, (character.deathTime - 1.0) / 20.0 * 1.6)));
            r = r.mul(spin(FORWARD, fall * 90.0));
        } else if (character.spinning) {
            r = r.mul(spin(RIGHT, -90.0 - pitch)).mul(spin(Vector3.UP, ageInTicks * -75.0));
        } else if (character.sleeping) {
            r = r.mul(spin(FORWARD, 90.0)).mul(spin(Vector3.UP, 270.0));
        } else if (character.upsideDown) {
            at = new Vector3(0, character.height + 0.1, 0);
            r = r.mul(spin(FORWARD, 180.0));
        }

        if (character.flying) {
            double onset = Math.min(1.0, character.flyingTime * character.flyingTime / 100.0);
            if (!character.spinning) r = r.mul(spin(RIGHT, onset * (-90.0 - pitch)));
            r = r.mul(Quat.axisAngle(Vector3.UP, character.flyingYaw));
        } else if (character.swimAmount > 0) {
            double target = character.inWater ? -90.0 - pitch : -90.0;
            r = r.mul(spin(RIGHT, character.swimAmount * target));
            if (character.crawling) at = at.add(new Vector3(0, -1, 0.3));
        }
        return new CFrame(at, r);
    }

    private static Quat spin(Vector3 axis, double degrees) {
        return Quat.axisAngle(axis, Math.toRadians(degrees));
    }

    private static void arms(Character character, Turn head, Turn rightArm, Turn leftArm) {
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

        pose(character, head, rightArm, leftArm, first);
        ArmPose firstPose = first ? character.rightArmPose : character.leftArmPose;
        if (!firstPose.affectsOther()) pose(character, head, rightArm, leftArm, !first);
    }

    private static void pose(Character character, Turn head, Turn rightArm, Turn leftArm, boolean right) {
        Turn arm = right ? rightArm : leftArm;
        Turn other = right ? leftArm : rightArm;
        double side = right ? 1 : -1;
        switch (right ? character.rightArmPose : character.leftArmPose) {
            case EMPTY -> arm.y = 0;
            case BLOCK -> block(head, arm, right);
            case ITEM -> lower(arm, 0.31415927);
            case TRIDENT -> lower(arm, 3.1415927);
            case BRUSH -> lower(arm, 0.62831855);
            case BOW -> {
                arm.y = -0.1 * side + head.y;
                other.y = 0.1 * side + head.y + 0.4 * side;
                rightArm.x = -1.5707964 + head.x;
                leftArm.x = -1.5707964 + head.x;
            }
            case CROSSBOW_CHARGE -> charge(character, rightArm, leftArm, right);
            case CROSSBOW_HOLD -> hold(head, rightArm, leftArm, right);
            case SPYGLASS -> {
                arm.x = Math.clamp(head.x - 1.9198622 - (character.crouching ? 0.2617994 : 0), -2.4, 3.3);
                arm.y = head.y - 0.2617994 * side;
            }
            case HORN -> {
                arm.x = Math.clamp(head.x, -1.2, 1.2) - 1.4835298;
                arm.y = head.y - 0.5235988 * side;
            }
            case SPEAR -> spear(character, head, arm, right);
        }
    }

    private static void lower(Turn arm, double by) {
        arm.x = arm.x * 0.5 - by;
        arm.y = 0;
    }

    private static void spear(Character character, Turn head, Turn arm, boolean right) {
        int invert = right ? 1 : -1;
        arm.y = -0.1 * invert + head.y;
        arm.x = -1.5707964 + head.x + 0.8;
        if (character.flying || character.swimAmount > 0) arm.x -= 0.9599311;
        arm.y = Math.clamp(arm.y, Math.toRadians(-60), Math.toRadians(60));
        arm.x = Math.clamp(arm.x, Math.toRadians(-120), Math.toRadians(30));
    }

    private static void block(Turn head, Turn arm, boolean right) {
        arm.x = arm.x * 0.5 - 0.9424779 + Math.clamp(head.x, -Math.PI * 4.0 / 9.0, 0.43633232);
        arm.y = (right ? -30.0 : 30.0) * (Math.PI / 180.0)
                + Math.clamp(head.y, -Math.PI / 6, Math.PI / 6);
    }

    private static void charge(Character character, Turn rightArm, Turn leftArm, boolean right) {
        Turn holding = right ? rightArm : leftArm;
        Turn pulling = right ? leftArm : rightArm;
        holding.y = right ? -0.8 : 0.8;
        holding.x = -0.97079635;
        pulling.x = holding.x;
        double wound = character.chargeProgress;
        pulling.y = lerp(wound, 0.4, 0.85) * (right ? 1 : -1);
        pulling.x = lerp(wound, pulling.x, -Math.PI / 2);
    }

    private static void hold(Turn head, Turn rightArm, Turn leftArm, boolean right) {
        Turn holding = right ? rightArm : leftArm;
        Turn shooting = right ? leftArm : rightArm;
        holding.y = (right ? -0.3 : 0.3) + head.y;
        shooting.y = (right ? 0.6 : -0.6) + head.y;
        holding.x = -Math.PI / 2 + head.x + 0.1;
        shooting.x = -1.5 + head.x;
    }

    private static void swing(Character character, Turn head, Turn torso,
                              Turn rightArm, Turn leftArm) {
        double attack = character.attackTime;
        if (attack <= 0) return;

        double twist = Math.sin(Math.sqrt(attack) * Math.PI * 2) * 0.2;
        if (character.attackLeft) twist = -twist;
        torso.y = twist;

        rightArm.atX += 5.0 - Math.cos(twist) * 5.0;
        rightArm.atZ += Math.sin(twist) * 5.0;
        leftArm.atX += Math.cos(twist) * 5.0 - 5.0;
        leftArm.atZ += -Math.sin(twist) * 5.0;

        rightArm.y += twist;
        leftArm.y += twist;
        leftArm.x += twist;

        double eased = 1.0 - Math.pow(1.0 - attack, 4);
        double reach = Math.sin(eased * Math.PI);
        double aim = Math.sin(attack * Math.PI) * -(head.x - 0.7) * 0.75;

        Turn arm = character.attackLeft ? leftArm : rightArm;
        arm.x -= reach * 1.2 + aim;
        arm.y += twist * 2.0;
        arm.z += Math.sin(attack * Math.PI) * -0.4;
    }

    private static void swim(Character character, Turn rightArm, Turn leftArm,
                             Turn rightLeg, Turn leftLeg) {
        double amount = character.swimAmount;
        if (amount <= 0) return;

        double arms = character.usingItem ? 0 : amount;
        double right = character.rightArmPose == ArmPose.SPEAR
                || character.attackTime > 0 && !character.attackLeft ? 0 : arms;
        double left = character.leftArmPose == ArmPose.SPEAR
                || character.attackTime > 0 && character.attackLeft ? 0 : arms;

        double pos = character.moveDistance % 26.0;
        if (pos < 14.0) {
            leftArm.x = rotLerp(left, leftArm.x, 0);
            rightArm.x = lerp(right, rightArm.x, 0);
            leftArm.y = rotLerp(left, leftArm.y, Math.PI);
            rightArm.y = lerp(right, rightArm.y, Math.PI);
            leftArm.z = rotLerp(left, leftArm.z,
                    Math.PI + 1.8707964 * reach(pos) / reach(14.0));
            rightArm.z = lerp(right, rightArm.z,
                    Math.PI - 1.8707964 * reach(pos) / reach(14.0));
        } else if (pos < 22.0) {
            double through = (pos - 14.0) / 8.0;
            leftArm.x = rotLerp(left, leftArm.x, Math.PI / 2 * through);
            rightArm.x = lerp(right, rightArm.x, Math.PI / 2 * through);
            leftArm.y = rotLerp(left, leftArm.y, Math.PI);
            rightArm.y = lerp(right, rightArm.y, Math.PI);
            leftArm.z = rotLerp(left, leftArm.z, 5.012389 - 1.8707964 * through);
            rightArm.z = lerp(right, rightArm.z, 1.2707963 + 1.8707964 * through);
        } else {
            double through = (pos - 22.0) / 4.0;
            leftArm.x = rotLerp(left, leftArm.x, Math.PI / 2 - Math.PI / 2 * through);
            rightArm.x = lerp(right, rightArm.x, Math.PI / 2 - Math.PI / 2 * through);
            leftArm.y = rotLerp(left, leftArm.y, Math.PI);
            rightArm.y = lerp(right, rightArm.y, Math.PI);
            leftArm.z = rotLerp(left, leftArm.z, Math.PI);
            rightArm.z = lerp(right, rightArm.z, Math.PI);
        }

        double kick = character.moveDistance * 0.33333334;
        leftLeg.x = lerp(amount, leftLeg.x, 0.3 * Math.cos(kick + Math.PI));
        rightLeg.x = lerp(amount, rightLeg.x, 0.3 * Math.cos(kick));
    }

    private static double reach(double at) {
        return -65.0 * at + at * at;
    }

    private static void wings(Character character, double scale) {
        Wings pair = Rig.wings(character);
        if (pair == null || !pair.worn) return;
        Turn right = new Turn();
        Turn left = new Turn();

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

    private static void spin(Character character, double ageInTicks, double scale) {
        if (!character.spinning) return;
        for (int n = 0; n < Rig.SPIN.length; n++) {
            Turn shell = new Turn();
            shell.y = -Math.toRadians(wrapDegrees(ageInTicks * -(45.0 + (n + 1) * 5.0)));
            turn(character, Rig.SPIN[n], shell, scale);
        }
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static void cape(Character character) {
        if (!(Rig.joint(character, Rig.CAPE) instanceof Joint hinge)) return;
        if (!(hinge.part1 instanceof Cape cape) || !cape.visible) return;

        double tilt = Math.toRadians(6.0 + cape.lean / 2.0 + cape.flap);
        double roll = Math.toRadians(cape.sway / 2.0);
        double swing = Math.toRadians(180.0 - cape.sway / 2.0);

        Quat rotation = Quat.axisAngle(Vector3.UP, Math.PI)
                .mul(Quat.axisAngle(RIGHT, -tilt))
                .mul(Quat.axisAngle(FORWARD, roll))
                .mul(Quat.axisAngle(Vector3.UP, -swing));
        Instances.setObj(hinge, TRANSFORM, new CFrame(Vector3.ZERO, rotation));
    }

    private static void turn(Character character, String name, Turn limb, double scale) {
        if (!(Rig.joint(character, name) instanceof Joint hinge)) return;
        Quat rotation = Quat.axisAngle(FORWARD, limb.z)
                .mul(Quat.axisAngle(Vector3.UP, -limb.y))
                .mul(Quat.axisAngle(RIGHT, -limb.x));
        Vector3 at = Rig.offset(limb.atX, limb.atY, limb.atZ).mul(scale);
        Instances.setObj(hinge, TRANSFORM, new CFrame(at, rotation));
    }

    private static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    private static double rotLerp(double t, double from, double to) {
        double difference = (to - from) % (Math.PI * 2);
        if (difference >= Math.PI) difference -= Math.PI * 2;
        if (difference < -Math.PI) difference += Math.PI * 2;
        return from + difference * t;
    }
}
