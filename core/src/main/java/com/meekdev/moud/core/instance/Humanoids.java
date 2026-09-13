package com.meekdev.moud.core.instance;

import java.util.Map;
import java.util.WeakHashMap;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

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
            became(living, HumanoidState.STANDING);
        }

        if (!character.owner.isEmpty()) return;
        walk(character, living, dt);
        hop(character, living, dt);
    }

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
            if (living.state != HumanoidState.SEATED) became(living, HumanoidState.STANDING);
            return;
        }

        Vec3 at = Transforms.world(character).position();
        Vec3 toward = living.walkTo.sub(at);
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
        moved = new Vec3(moved.x(), at.y() + toward.y() * (step / away), moved.z());
        Ground ground = GROUNDS.get(character.tree());
        double floor = ground == null ? Double.NaN : ground.below(moved.x(), at.y(), moved.z());
        if (!Double.isNaN(floor)) {
            double y = floor > at.y() ? Math.min(floor, at.y() + RISE * dt) : Math.max(floor, at.y() - SINK * dt);
            moved = new Vec3(moved.x(), y, moved.z());
        }

        double yaw = Math.atan2(-way.x(), -way.z());
        Vec3 facing = Transforms.world(character).rotation().rotate(new Vec3(0, 0, -1));
        double now = Math.atan2(-facing.x(), -facing.z());
        double turn = Math.IEEEremainder(yaw - now, Math.PI * 2);
        double most = TURN_RATE * dt;
        yaw = now + Math.max(-most, Math.min(most, turn));
        Instances.setObj(character, CFRAME, Transforms.localFor(character,
                new CFrame(moved, Quat.euler(0, yaw, 0))));

        Instances.setNum(character, MOVE_DISTANCE, character.moveDistance + step * 4.0);
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, living.walkSpeed / 4.317));
        became(living, HumanoidState.RUNNING);
    }

    private static void became(Humanoid living, HumanoidState next) {
        if (living.state == next) return;
        Instances.setObj(living, STATE, next);
        living.stateChanged.fire(living);
        if (next == HumanoidState.DEAD) living.died.fire(living);
    }
}
