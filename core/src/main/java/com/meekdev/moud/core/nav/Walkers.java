package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class Walkers {

    private static final double REPATH = 0.5;
    private static final double RECHECK = 0.25;
    private static final double CLIMB_SPEED = 0.55;
    private static final double FIRST_STEP = 0.05;
    private static final double LONGEST_STEP = 0.1;
    private static final double REST_SLACK = 1.5;
    private static final double ARRIVE_SLACK = 0.05;
    private static final double LEVEL = 0.02;
    private static final double EXIT_REACH = 0.1;
    private static final double LADDER_REACH = 0.05;
    private static final double PLAYER_ARRIVE = 0.6;
    private static final double PLAYER_ARRIVE_HEIGHT = 1.5;
    private static final double STILL = 1e-6;

    private static final PropertyDef JUMP = Classes.HUMANOID.property("jump");
    private static final PropertyDef WALKING = Classes.HUMANOID.property("walking");
    private static final PropertyDef WALK_TO = Classes.HUMANOID.property("walkTo");
    private static final PropertyDef CFRAME = Classes.CHARACTER.property("cframe");

    private enum Outcome { GOING, DONE, ARRIVED, TARGET_GONE, NO_WAY }

    private static final class Plan {
        List<Waypoint> waypoints;
        int next;
        int entered = -1;
        Instance target;
        double distance;
        double lastRepath = Double.NEGATIVE_INFINITY;
        Vector3 lastTarget;
        boolean resting;
        Vector3 goal;
        Agent agent = Agent.PLAYER;
        boolean partial;
        double lastCheck = Double.NEGATIVE_INFINITY;
        Object source;
    }

    private static final Map<InstanceTree, Walkers> TREES = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<Character, Plan> plans = new HashMap<>();
    private double last = Double.NaN;

    public interface Pilot {
        void walk(Character body, List<Waypoint> waypoints);

        void jump(Character body);

        void stop(Character body);

        void move(Character body, Vector3 direction, boolean relativeToCamera);
    }

    public interface Finder {
        Route find(Vector3 from, Vector3 to, Agent agent);

        int blocked(Vector3 from, List<Vector3> ahead, Agent agent);
    }

    private static Pilot pilot;

    public static void pilot(Pilot value) {
        pilot = value;
    }

    private Walkers() {}

    private static Map<Character, Plan> plans(Character body) {
        InstanceTree tree = body.tree();
        return tree == null ? new HashMap<>() : TREES.computeIfAbsent(tree, t -> new Walkers()).plans;
    }

    public static void walk(Character body, List<Waypoint> waypoints) {
        start(body, waypoints, null, Agent.PLAYER, false, null);
    }

    public static void walk(Character body, List<Waypoint> waypoints, Vector3 goal, Agent agent, boolean partial) {
        start(body, waypoints, goal, agent, partial, null);
    }

    public static void walkAlong(Character body, List<Waypoint> waypoints, Agent agent, Object source) {
        start(body, waypoints, null, agent, false, source);
    }

    public static boolean walking(Character body, Object source) {
        Plan plan = plans(body).get(body);
        return plan != null && source != null && plan.source == source;
    }

    private static void start(Character body, List<Waypoint> waypoints, Vector3 goal, Agent agent, boolean partial, Object source) {
        Plan plan = new Plan();
        plan.waypoints = waypoints;
        plan.goal = goal;
        plan.agent = agent;
        plan.partial = partial;
        plan.source = source;
        plans(body).put(body, plan);
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

    public static void move(Character body, Vector3 direction, boolean relativeToCamera) {
        if (body.hasPlayer()) {
            if (pilot != null) pilot.move(body, direction, relativeToCamera);
            return;
        }
        plans(body).remove(body);
        Humanoids.move(body, direction);
    }

    public static void cancelled(Character body) {
        plans(body).remove(body);
    }

    public static void follow(Character body, Instance target, double distance, Agent agent) {
        Plan plan = new Plan();
        plan.target = target;
        plan.distance = distance;
        plan.agent = agent;
        plans(body).put(body, plan);
    }

    public static void stop(Character body) {
        plans(body).remove(body);
        if (body.hasPlayer() && pilot != null) pilot.stop(body);
        Humanoid living = Rig.humanoid(body);
        if (living != null && body.isAlive()) {
            Instances.setBool(living, WALKING, false);
            if (living.state == HumanoidState.CLIMBING) Humanoids.changeState(body, HumanoidState.STANDING);
        }
    }

    public static boolean busy(Character body) {
        return plans(body).containsKey(body);
    }

    public static void step(InstanceTree tree, Finder finder, double now) {
        Walkers walkers = tree == null ? null : TREES.get(tree);
        if (walkers == null) return;
        double dt = Double.isNaN(walkers.last) ? FIRST_STEP : Math.clamp(now - walkers.last, 0, LONGEST_STEP);
        walkers.last = now;
        RuntimeException failure = null;
        for (Character body : new ArrayList<>(walkers.plans.keySet())) {
            Plan plan = walkers.plans.get(body);
            if (plan == null) continue;
            Humanoid living = body.isAlive() ? Rig.humanoid(body) : null;
            if (living == null || living.state == HumanoidState.DEAD) {
                walkers.plans.remove(body);
                continue;
            }
            Outcome outcome;
            try {
                outcome = advance(body, living, plan, finder, now, dt);
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                outcome = Outcome.NO_WAY;
            }
            if (outcome == Outcome.GOING) continue;
            walkers.plans.remove(body, plan);
            finish(body, living, outcome);
        }
        if (failure != null) throw failure;
    }

    private static Outcome advance(Character body, Humanoid living, Plan plan, Finder finder, double now, double dt) {
        Vector3 at = Transforms.world(body).position();
        if (plan.target != null) {
            if (!plan.target.isAlive()) return Outcome.TARGET_GONE;
            if (!chase(body, living, plan, finder, at, now)) return Outcome.GOING;
        }
        if (dueForCheck(plan, now)) {
            plan.lastCheck = now;
            if (blocked(finder, plan, at)) {
                replan(body, plan, finder, at, plan.goal != null ? plan.goal : plan.lastTarget);
                if (plan.waypoints == null && plan.target == null) return Outcome.NO_WAY;
            }
        }
        if (body.hasPlayer()) return followPlayerPath(plan, at);
        if (plan.waypoints == null || plan.waypoints.isEmpty()) return plan.target == null ? Outcome.DONE : Outcome.GOING;
        if (walk(body, living, plan, at, dt)) return Outcome.GOING;
        if (plan.target == null) return Outcome.DONE;
        plan.waypoints = null;
        return Outcome.GOING;
    }

    private static void finish(Character body, Humanoid living, Outcome outcome) {
        switch (outcome) {
            case TARGET_GONE -> Instances.setBool(living, WALKING, false);
            case NO_WAY -> {
                if (body.hasPlayer() && pilot != null) pilot.stop(body);
                Instances.setBool(living, WALKING, false);
            }
            case ARRIVED -> living.arrived.fire(living);
            case GOING, DONE -> {}
        }
    }

    private static boolean chase(Character body, Humanoid living, Plan plan, Finder finder, Vector3 at, double now) {
        Vector3 goal = Transforms.world(plan.target).position();
        double reach = plan.resting ? plan.distance + REST_SLACK : plan.distance;
        if (goal.sub(at).lengthSq() <= reach * reach) {
            plan.resting = true;
            if (body.hasPlayer() && plan.waypoints != null && pilot != null) pilot.stop(body);
            plan.waypoints = null;
            Instances.setBool(living, WALKING, false);
            return false;
        }
        if (plan.resting) {
            plan.resting = false;
            plan.waypoints = null;
        }
        boolean targetMoved = plan.lastTarget == null || plan.lastTarget.sub(goal).lengthSq() > 1;
        if ((plan.waypoints == null || targetMoved) && now - plan.lastRepath >= REPATH) {
            replan(body, plan, finder, at, goal);
            plan.lastRepath = now;
            plan.lastTarget = goal;
        }
        return true;
    }

    private static boolean dueForCheck(Plan plan, double now) {
        boolean hasPath = plan.waypoints != null && !plan.waypoints.isEmpty();
        boolean hasGoal = plan.goal != null || plan.target != null;
        return hasPath && hasGoal && now - plan.lastCheck >= RECHECK;
    }

    private static void replan(Character body, Plan plan, Finder finder, Vector3 at, Vector3 goal) {
        Route route = finder.find(at, goal, plan.agent);
        boolean usable = route.status() == Route.Status.SUCCESS || route.found() && (plan.partial || plan.target != null);
        plan.waypoints = usable ? new ArrayList<>(route.ahead()) : null;
        plan.next = 0;
        plan.entered = -1;
        if (body.hasPlayer() && pilot != null && plan.waypoints != null) pilot.walk(body, plan.waypoints);
    }

    private static boolean walk(Character body, Humanoid living, Plan plan, Vector3 at, double dt) {
        int index = Math.min(plan.next, plan.waypoints.size() - 1);
        Waypoint waypoint = plan.waypoints.get(index);
        Vector3 from = index == 0 ? at : plan.waypoints.get(index - 1).position();
        if (index != plan.entered) {
            plan.entered = index;
            if (waypoint.action() == Waypoint.Action.JUMP) Instances.setBool(living, JUMP, true);
        }
        boolean climbing = waypoint.action() == Waypoint.Action.CLIMB;
        if (climbing && climb(body, living, from, waypoint.position(), at, dt)) return true;
        if (living.state == HumanoidState.CLIMBING) Humanoids.changeState(body, HumanoidState.STANDING);
        Vector3 target = waypoint.position();
        if (flat(target, at).length() <= living.walkRadius + ARRIVE_SLACK) {
            plan.next++;
            if (plan.next >= plan.waypoints.size()) return false;
            return walk(body, living, plan, at, dt);
        }
        Instances.setObj(living, WALK_TO, target);
        Instances.setBool(living, WALKING, true);
        return true;
    }

    private static boolean climb(Character body, Humanoid living, Vector3 from, Vector3 to, Vector3 at, double dt) {
        boolean up = to.y() > from.y();
        Vector3 toLadder = flat(up ? from : to, at);
        Vector3 toExit = flat(to, at);
        double rise = living.walkSpeed * CLIMB_SPEED * dt;
        Vector3 across;
        double y;
        if (up && at.y() < to.y() - LEVEL) {
            across = toLadder;
            y = Math.min(to.y(), at.y() + rise);
        } else if (up && toExit.length() > EXIT_REACH) {
            across = toExit;
            y = to.y();
        } else if (!up && toLadder.length() > LADDER_REACH) {
            across = toLadder;
            y = at.y();
        } else if (!up && at.y() > to.y() + LEVEL) {
            across = Vector3.ZERO;
            y = Math.max(to.y(), at.y() - rise);
        } else {
            return false;
        }
        Vector3 stride = stride(across, living.walkSpeed * dt);
        Vector3 moved = new Vector3(at.x() + stride.x(), y, at.z() + stride.z());
        Instances.setBool(living, WALKING, false);
        if (living.state != HumanoidState.CLIMBING) Humanoids.changeState(body, HumanoidState.CLIMBING);
        CFrame world = Transforms.world(body);
        Instances.setObj(body, CFRAME, Transforms.localFor(body, world.withPosition(moved)));
        return true;
    }

    private static Vector3 stride(Vector3 across, double longest) {
        double length = across.length();
        if (length < STILL) return Vector3.ZERO;
        return across.mul(Math.min(length, longest) / length);
    }

    private static Vector3 flat(Vector3 to, Vector3 from) {
        return new Vector3(to.x() - from.x(), 0, to.z() - from.z());
    }

    private static boolean blocked(Finder finder, Plan plan, Vector3 at) {
        int next = Math.min(plan.next, plan.waypoints.size() - 1);
        List<Vector3> ahead = new ArrayList<>();
        for (int i = next; i < plan.waypoints.size(); i++) ahead.add(plan.waypoints.get(i).position());
        return finder.blocked(at, ahead, plan.agent) >= 0;
    }

    private static Outcome followPlayerPath(Plan plan, Vector3 at) {
        if (plan.waypoints == null || plan.waypoints.isEmpty()) return plan.target == null ? Outcome.DONE : Outcome.GOING;
        Vector3 last = plan.waypoints.getLast().position();
        boolean near = flat(last, at).length() <= PLAYER_ARRIVE && Math.abs(last.y() - at.y()) <= PLAYER_ARRIVE_HEIGHT;
        if (!near) return Outcome.GOING;
        if (plan.target == null) return Outcome.ARRIVED;
        plan.waypoints = null;
        return Outcome.GOING;
    }
}
