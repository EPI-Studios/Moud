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
    }

    private static final Map<Character, Plan> PLANS = new HashMap<>();

    private Walkers() {}

    public static void walk(Character body, List<Vec3> waypoints) {
        Plan plan = new Plan();
        plan.waypoints = waypoints;
        PLANS.put(body, plan);
    }

    public static void follow(Character body, Instance target, double distance) {
        Plan plan = new Plan();
        plan.target = target;
        plan.distance = distance;
        PLANS.put(body, plan);
    }

    public static void stop(Character body) {
        PLANS.remove(body);
        Humanoid living = Rig.humanoid(body);
        if (living != null && body.isAlive()) Instances.setBool(living, Classes.HUMANOID.property("walking"), false);
    }

    public static boolean busy(Character body) {
        return PLANS.containsKey(body);
    }

    public static void step(GridPath.Terrain terrain, double now) {
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
                if (goal.sub(at).lengthSq() <= plan.distance * plan.distance) {
                    plan.waypoints = null;
                    Instances.setBool(living, Classes.HUMANOID.property("walking"), false);
                    continue;
                }
                boolean moved = plan.lastTarget == null || plan.lastTarget.sub(goal).lengthSq() > 1;
                if (plan.waypoints == null || moved && now - plan.lastRepath >= REPATH) {
                    plan.waypoints = GridPath.find(terrain, at, goal, GridPath.Options.DEFAULT);
                    plan.next = 0;
                    plan.lastRepath = now;
                    plan.lastTarget = goal;
                }
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
}
