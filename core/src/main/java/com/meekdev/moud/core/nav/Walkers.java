package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Humanoid;
import com.meekdev.moud.core.instance.HumanoidState;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// bodies walking a path or following something, one waypoint at a time through humanoid.walkTo
public final class Walkers {

    // how often a follower looks again for where its target went, in seconds
    private static final double REPATH = 0.5;

    private static final class Plan {
        List<Vec3> waypoints;
        int next;
        Instance target;
        double distance;
        double lastRepath = -1e9;
        Vec3 lastTarget;
        boolean resting;
    }

    private static final Map<Character, Plan> PLANS = new HashMap<>();

    // moves a body a player is wearing, which the engine cannot walk itself: the player's own client
    // does the walking, and this is how it is told
    public interface Pilot {
        void walk(Character body, List<Vec3> waypoints);

        void jump(Character body);

        void stop(Character body);
    }

    private static Pilot pilot;

    public static void pilot(Pilot value) {
        pilot = value;
    }

    private Walkers() {}

    public static void walk(Character body, List<Vec3> waypoints) {
        Plan plan = new Plan();
        plan.waypoints = waypoints;
        PLANS.put(body, plan);
        if (body.worn() && pilot != null) pilot.walk(body, waypoints);
    }

    public static void jump(Character body) {
        if (body.worn()) {
            if (pilot != null) pilot.jump(body);
            return;
        }
        Humanoid living = Rig.humanoid(body);
        if (living != null) Instances.setBool(living, Classes.HUMANOID.property("jump"), true);
    }

    // the player took the controls back, or their client gave up
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
        if (body.worn() && pilot != null) pilot.stop(body);
        Humanoid living = Rig.humanoid(body);
        if (living != null && body.isAlive()) Instances.setBool(living, Classes.HUMANOID.property("walking"), false);
    }

    public static boolean busy(Character body) {
        return PLANS.containsKey(body);
    }

    // where paths come from: the place's navmesh
    @FunctionalInterface
    public interface Finder {
        List<Vec3> find(Vec3 from, Vec3 to, boolean partial);
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
            Vec3 at = Transforms.world(body).position();
            if (plan.target != null) {
                if (!plan.target.isAlive()) {
                    it.remove();
                    Instances.setBool(living, Classes.HUMANOID.property("walking"), false);
                    continue;
                }
                Vec3 goal = Transforms.world(plan.target).position();
                // stopped until the target is a clear step further off, so a follower does not start and
                // stop on every little move of the one it follows
                double reach = plan.resting ? plan.distance + 1.5 : plan.distance;
                if (goal.sub(at).lengthSq() <= reach * reach) {
                    plan.resting = true;
                    if (body.worn() && plan.waypoints != null && pilot != null) pilot.stop(body);
                    plan.waypoints = null;
                    Instances.setBool(living, Classes.HUMANOID.property("walking"), false);
                    continue;
                }
                if (plan.resting) {
                    plan.resting = false;
                    plan.waypoints = null;
                }
                boolean moved = plan.lastTarget == null || plan.lastTarget.sub(goal).lengthSq() > 1;
                if (plan.waypoints == null || moved && now - plan.lastRepath >= REPATH) {
                    // as close as it can get: a follower whose target stands somewhere unreachable still comes over
                    plan.waypoints = finder.find(at, goal, true);
                    plan.next = 0;
                    plan.lastRepath = now;
                    plan.lastTarget = goal;
                    if (body.worn() && pilot != null && plan.waypoints != null) pilot.walk(body, plan.waypoints);
                }
            }
            if (body.worn()) {
                steer(body, living, plan, at, it);
                continue;
            }
            if (plan.waypoints == null || plan.waypoints.isEmpty()) {
                if (plan.target == null) it.remove();
                continue;
            }
            Vec3 waypoint = plan.waypoints.get(Math.min(plan.next, plan.waypoints.size() - 1));
            Vec3 flat = new Vec3(waypoint.x() - at.x(), 0, waypoint.z() - at.z());
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
            Instances.setObj(living, Classes.HUMANOID.property("walkTo"), waypoint);
            Instances.setBool(living, Classes.HUMANOID.property("walking"), true);
        }
    }

    // a player's body is walked by its client; the server only watches for it getting there
    private static void steer(Character body, Humanoid living, Plan plan, Vec3 at, Iterator<Map.Entry<Character, Plan>> it) {
        if (plan.waypoints == null || plan.waypoints.isEmpty()) {
            if (plan.target == null) it.remove();
            return;
        }
        Vec3 last = plan.waypoints.getLast();
        Vec3 flat = new Vec3(last.x() - at.x(), 0, last.z() - at.z());
        if (flat.length() > 0.6 || Math.abs(last.y() - at.y()) > 1.5) return;
        if (plan.target == null) {
            it.remove();
            living.arrived.fire(living);
        } else {
            plan.waypoints = null;
        }
    }
}
