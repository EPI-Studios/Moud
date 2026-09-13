package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Walkers {

    private static final double REPATH = 0.5;

    private static final PropertyDef JUMP = Classes.HUMANOID.property("jump");
    private static final PropertyDef WALKING = Classes.HUMANOID.property("walking");
    private static final PropertyDef WALK_TO = Classes.HUMANOID.property("walkTo");

    private static final class Plan {
        List<Vector3> waypoints;
        int next;
        Instance target;
        double distance;
        double lastRepath = -1e9;
        Vector3 lastTarget;
        boolean resting;
    }

    private static final Map<Character, Plan> PLANS = new HashMap<>();

    public interface Pilot {
        void walk(Character body, List<Vector3> waypoints);

        void jump(Character body);

        void stop(Character body);
    }

    private static Pilot pilot;

    public static void pilot(Pilot value) {
        pilot = value;
    }

    private Walkers() {}

    public static void walk(Character body, List<Vector3> waypoints) {
        Plan plan = new Plan();
        plan.waypoints = waypoints;
        PLANS.put(body, plan);
        if (body.hasPlayer() && pilot != null) pilot.walk(body, waypoints);
    }

    public static void jump(Character body) {
        if (body.hasPlayer()) {
            if (pilot != null) pilot.jump(body);
            return;
        }
        Humanoid living = Rig.humanoid(body);
        if (living != null) Instances.setBool(living, JUMP, true);
    }

    public static void cancelled(Character body) {
        PLANS.remove(body);
    }

    public static void follow(Character body, Instance target, double distance) {
        Plan plan = new Plan();
        plan.target = target;
        plan.distance = distance;
        PLANS.put(body, plan);
    }

    public static void stop(Character body) {
        PLANS.remove(body);
        if (body.hasPlayer() && pilot != null) pilot.stop(body);
        Humanoid living = Rig.humanoid(body);
        if (living != null && body.isAlive()) Instances.setBool(living, WALKING, false);
    }

    public static boolean busy(Character body) {
        return PLANS.containsKey(body);
    }

    @FunctionalInterface
    public interface Finder {
        List<Vector3> find(Vector3 from, Vector3 to, boolean partial);
    }

    public static void step(Finder finder, double now) {
        for (Iterator<Map.Entry<Character, Plan>> it = PLANS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Character, Plan> entry = it.next();
            Character body = entry.getKey();
            Plan plan = entry.getValue();
            Humanoid living = body.isAlive() ? Rig.humanoid(body) : null;
            if (living == null || living.state == HumanoidState.DEAD) {
                it.remove();
                continue;
            }
            Vector3 at = Transforms.world(body).position();
            if (plan.target != null) {
                if (!plan.target.isAlive()) {
                    it.remove();
                    Instances.setBool(living, WALKING, false);
                    continue;
                }
                Vector3 goal = Transforms.world(plan.target).position();
                double reach = plan.resting ? plan.distance + 1.5 : plan.distance;
                if (goal.sub(at).lengthSq() <= reach * reach) {
                    plan.resting = true;
                    if (body.hasPlayer() && plan.waypoints != null && pilot != null) pilot.stop(body);
                    plan.waypoints = null;
                    Instances.setBool(living, WALKING, false);
                    continue;
                }
                if (plan.resting) {
                    plan.resting = false;
                    plan.waypoints = null;
                }
                boolean moved = plan.lastTarget == null || plan.lastTarget.sub(goal).lengthSq() > 1;
                if (plan.waypoints == null || moved && now - plan.lastRepath >= REPATH) {
                    plan.waypoints = finder.find(at, goal, true);
                    plan.next = 0;
                    plan.lastRepath = now;
                    plan.lastTarget = goal;
                    if (body.hasPlayer() && pilot != null && plan.waypoints != null) pilot.walk(body, plan.waypoints);
                }
            }
            if (body.hasPlayer()) {
                followPlayerPath(body, living, plan, at, it);
                continue;
            }
            if (plan.waypoints == null || plan.waypoints.isEmpty()) {
                if (plan.target == null) it.remove();
                continue;
            }
            Vector3 waypoint = plan.waypoints.get(Math.min(plan.next, plan.waypoints.size() - 1));
            Vector3 flat = new Vector3(waypoint.x() - at.x(), 0, waypoint.z() - at.z());
            if (flat.length() <= living.walkRadius + 0.05) {
                plan.next++;
                if (plan.next >= plan.waypoints.size()) {
                    if (plan.target == null) {
                        it.remove();
                    } else {
                        plan.waypoints = null;
                    }
                    continue;
                }
                waypoint = plan.waypoints.get(plan.next);
            }
            Instances.setObj(living, WALK_TO, waypoint);
            Instances.setBool(living, WALKING, true);
        }
    }

    private static void followPlayerPath(Character body, Humanoid living, Plan plan, Vector3 at, Iterator<Map.Entry<Character, Plan>> it) {
        if (plan.waypoints == null || plan.waypoints.isEmpty()) {
            if (plan.target == null) it.remove();
            return;
        }
        Vector3 last = plan.waypoints.getLast();
        Vector3 flat = new Vector3(last.x() - at.x(), 0, last.z() - at.z());
        if (flat.length() > 0.6 || Math.abs(last.y() - at.y()) > 1.5) return;
        if (plan.target == null) {
            it.remove();
            living.arrived.fire(living);
        } else {
            plan.waypoints = null;
        }
    }
}
