package com.meekdev.moud.core.character;

import java.util.Map;
import java.util.WeakHashMap;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;

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

        double was = living.health;
        double health = Math.clamp(living.health, 0, living.maxHealth);
        if (health != living.health) Instances.setNum(living, HEALTH, health);
        if (health != was) living.healthChanged.fire(living);

        if (health <= 0) {
            setState(living, HumanoidState.DEAD);
            Instances.setBool(living, WALKING, false);
            Instances.setNum(character, MOVE_SPEED, 0);
            return;
        }
        if (living.state == HumanoidState.DEAD) {
            setState(living, HumanoidState.STANDING);
        }

        if (!character.owner.isEmpty()) return;
        walk(character, living, dt);
        updateJump(character, living, dt);
    }

    private static final class Jump {
        double time;
        double height;
    }

    private static final Map<Character, Jump> JUMPS = new WeakHashMap<>();

    private static void updateJump(Character character, Humanoid living, double dt) {
        Jump jump = JUMPS.get(character);
        if (jump == null && living.jump) {
            jump = new Jump();
            JUMPS.put(character, jump);
            Instances.setBool(living, JUMP, false);
            setState(living, HumanoidState.JUMPING);
        }
        if (jump == null) return;
        double gravity = 32 * living.gravityScale;
        double before = jump.height;
        jump.time += dt;
        jump.height = Math.max(0, living.jumpPower * jump.time - 0.5 * gravity * jump.time * jump.time);
        CFrame frame = Transforms.world(character);
        Vector3 moved = frame.position().add(new Vector3(0, jump.height - before, 0));
        Instances.setObj(character, CFRAME, Transforms.localFor(character, frame.withPosition(moved)));
        if (jump.height <= 0 && jump.time > 0) {
            JUMPS.remove(character);
            setState(living, HumanoidState.STANDING);
        } else if (living.jumpPower - gravity * jump.time < 0) {
            setState(living, HumanoidState.FALLING);
        }
    }

    @FunctionalInterface
    public interface Ground {
        double below(double x, double y, double z);
    }

    private static final Map<InstanceTree, Ground> GROUNDS = new WeakHashMap<>();

    public static void ground(InstanceTree tree, Ground ground) {
        if (ground == null) {
            GROUNDS.remove(tree);
        } else {
            GROUNDS.put(tree, ground);
        }
    }

    private static final double RISE = 10;
    private static final double SINK = 14;

    private static final double TURN_RATE = 10;

    private static void walk(Character character, Humanoid living, double dt) {
        if (!living.walking || living.state == HumanoidState.SEATED) {
            if (character.moveSpeed != 0) Instances.setNum(character, MOVE_SPEED, 0);
            if (living.state != HumanoidState.SEATED) setState(living, HumanoidState.STANDING);
            return;
        }

        Vector3 at = Transforms.world(character).position();
        Vector3 toward = living.walkTo.sub(at);
        Vector3 flat = new Vector3(toward.x(), 0, toward.z());
        double away = flat.length();

        if (away <= living.walkRadius) {
            Instances.setBool(living, WALKING, false);
            Instances.setNum(character, MOVE_SPEED, 0);
            setState(living, HumanoidState.STANDING);
            living.arrived.fire(living);
            return;
        }

        double step = Math.min(away, living.walkSpeed * dt);
        Vector3 way = flat.mul(1.0 / away);
        Vector3 moved = at.add(way.mul(step));
        moved = new Vector3(moved.x(), at.y() + toward.y() * (step / away), moved.z());
        Ground ground = GROUNDS.get(character.tree());
        double floor = ground == null ? Double.NaN : ground.below(moved.x(), at.y(), moved.z());
        if (!Double.isNaN(floor)) {
            double y = floor > at.y() ? Math.min(floor, at.y() + RISE * dt) : Math.max(floor, at.y() - SINK * dt);
            moved = new Vector3(moved.x(), y, moved.z());
        }

        double yaw = Math.atan2(-way.x(), -way.z());
        Vector3 facing = Transforms.world(character).rotation().rotate(new Vector3(0, 0, -1));
        double now = Math.atan2(-facing.x(), -facing.z());
        double turn = Math.IEEEremainder(yaw - now, Math.PI * 2);
        yaw = now + Math.clamp(turn, -TURN_RATE * dt, TURN_RATE * dt);
        Instances.setObj(character, CFRAME, Transforms.localFor(character,
                new CFrame(moved, Quat.euler(0, yaw, 0))));

        Instances.setNum(character, MOVE_DISTANCE, character.moveDistance + step * 4.0);
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, living.walkSpeed / 4.317));
        setState(living, HumanoidState.RUNNING);
    }

    private static void setState(Humanoid living, HumanoidState next) {
        if (living.state == next) return;
        Instances.setObj(living, STATE, next);
        living.stateChanged.fire(living);
        if (next == HumanoidState.DEAD) living.died.fire(living);
    }
}
