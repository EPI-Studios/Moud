package com.meekdev.moud.core.instance;

import java.util.Map;
import java.util.WeakHashMap;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

// the living half of every body, once a tick
//
// two things happen here and nothing else does. life is kept inside its bounds and a body with
// none left is put in the state that says so -- the engine does not decide what dying means, it
// stops driving the body and lets a place decide. and a body that has been told to walk somewhere
// walks there
//
// that second one is what a character could not do at all. a player is driven by an entity; every
// other body in the world was furniture, posed by hand or standing still. telling one to walk is
// the difference between a scene and a game
public final class Humanoids {

    private static final PropertyDef HEALTH = Classes.HUMANOID.property("health");
    private static final PropertyDef STATE = Classes.HUMANOID.property("state");
    private static final PropertyDef WALKING = Classes.HUMANOID.property("walking");

    private static final PropertyDef CFRAME = Classes.CHARACTER.property("cframe");
    private static final PropertyDef JUMP = Classes.HUMANOID.property("jump");
    private static final PropertyDef MOVE_DISTANCE = Classes.CHARACTER.property("moveDistance");
    private static final PropertyDef MOVE_SPEED = Classes.CHARACTER.property("moveSpeed");

    private Humanoids() {}

    public static void follow(InstanceTree tree, double dt) {
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character) apply(character, dt);
        }
    }

    public static void apply(Character character, double dt) {
        Humanoid living = Rig.humanoid(character);
        if (living == null) return;

        // a place may write either of these to anything. the bound is the engine's job
        double was = living.health;
        double health = Math.max(0, Math.min(living.maxHealth, living.health));
        if (health != living.health) Instances.setNum(living, HEALTH, health);
        if (health != was) living.healthChanged.fire(living);

        if (health <= 0) {
            became(living, HumanoidState.DEAD);
            Instances.setBool(living, WALKING, false);
            Instances.setNum(character, MOVE_SPEED, 0);
            return;
        }
        if (living.state == HumanoidState.DEAD) {
            // brought back by a place writing health, which is the only way back out
            became(living, HumanoidState.STANDING);
        }

        // a body somebody else drives is walked by whatever drives it. this is for the ones
        // nobody does, which is every body a place made
        if (!character.owner.isEmpty()) return;
        walk(character, living, dt);
        hop(character, living, dt);
    }

    // a jump for a body nobody drives: straight up at jumpPower and back down to where it left from
    private static final Map<Character, double[]> HOPS = new WeakHashMap<>();

    private static void hop(Character character, Humanoid living, double dt) {
        double[] hop = HOPS.get(character);
        if (hop == null && living.jump) {
            hop = new double[] {0, 0};
            HOPS.put(character, hop);
            Instances.setBool(living, JUMP, false);
            became(living, HumanoidState.JUMPING);
        }
        if (hop == null) return;
        double gravity = 32 * living.gravityScale;
        double before = hop[1];
        hop[0] += dt;
        hop[1] = Math.max(0, living.jumpPower * hop[0] - 0.5 * gravity * hop[0] * hop[0]);
        CFrame frame = Transforms.world(character);
        Vec3 moved = frame.position().add(new Vec3(0, hop[1] - before, 0));
        Instances.setObj(character, CFRAME, Transforms.localFor(character, frame.withPosition(moved)));
        if (hop[1] <= 0 && hop[0] > 0) {
            HOPS.remove(character);
            became(living, HumanoidState.STANDING);
        } else if (living.jumpPower - gravity * hop[0] < 0) {
            became(living, HumanoidState.FALLING);
        }
    }

    private static void walk(Character character, Humanoid living, double dt) {
        if (!living.walking || living.state == HumanoidState.SEATED) {
            if (character.moveSpeed != 0) Instances.setNum(character, MOVE_SPEED, 0);
            if (living.state != HumanoidState.SEATED) became(living, HumanoidState.STANDING);
            return;
        }

        // where it is in the world, because that is where walkTo is stated. a body hanging off
        // something that moves has a frame stated against that, and comparing the two would have it
        // walking toward a point measured from the wrong origin
        Vec3 at = Transforms.world(character).position();
        Vec3 toward = living.walkTo.sub(at);
        // height is not a direction to walk in: a body told to go somewhere above it walks to
        // under it rather than into the air
        Vec3 flat = new Vec3(toward.x(), 0, toward.z());
        double away = flat.length();

        if (away <= living.walkRadius) {
            Instances.setBool(living, WALKING, false);
            Instances.setNum(character, MOVE_SPEED, 0);
            became(living, HumanoidState.STANDING);
            living.arrived.fire(living);
            return;
        }

        double step = Math.min(away, living.walkSpeed * dt);
        Vec3 way = flat.mul(1.0 / away);
        Vec3 moved = at.add(way.mul(step));
        // up or down a step as it goes, in proportion, so a path over uneven ground does not float
        moved = new Vec3(moved.x(), at.y() + toward.y() * (step / away), moved.z());

        // facing where it is going, the way a body that walks somewhere does. our forward is -z,
        // so the heading is measured from that rather than from +x
        double yaw = Math.atan2(-way.x(), -way.z());
        Instances.setObj(character, CFRAME, Transforms.localFor(character,
                new CFrame(moved, Quat.euler(0, yaw, 0))));

        // ground covered, not time elapsed: the walk cycle runs on distance, so feeding it the
        // step is what makes the legs match the speed rather than the frame rate
        Instances.setNum(character, MOVE_DISTANCE, character.moveDistance + step * 4.0);
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, living.walkSpeed / 4.317));
        became(living, HumanoidState.RUNNING);
    }

    // the state is written in one place, so the signal cannot be forgotten at one of them
    private static void became(Humanoid living, HumanoidState next) {
        if (living.state == next) return;
        Instances.setObj(living, STATE, next);
        living.stateChanged.fire(living);
        if (next == HumanoidState.DEAD) living.died.fire(living);
    }
}
