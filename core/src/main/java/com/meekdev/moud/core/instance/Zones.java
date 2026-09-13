package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Zones {

    private Zones() {}

    public static void step(InstanceTree tree, ClassRegistry classes, double now) {
        for (Zone zone : tree.ofClass(Classes.ZONE)) {
            if (!zone.isAlive()) continue;
            Set<Instance> now_ = new LinkedHashSet<>();
            if (zone.enabled) {
                for (Instance candidate : candidates(tree, classes, zone)) {
                    if (candidate != zone && contains(zone, Transforms.world(candidate).position())) now_.add(candidate);
                }
            }
            List<Instance> came = new ArrayList<>();
            List<Instance> went = new ArrayList<>();
            for (Instance instance : now_) {
                if (!zone.inside.contains(instance) && ready(zone, instance, now)) came.add(instance);
            }
            for (Instance instance : zone.inside) {
                if (!now_.contains(instance) && (!instance.isAlive() || ready(zone, instance, now))) went.add(instance);
            }
            for (Instance instance : came) {
                zone.inside.add(instance);
                zone.changedAt.put(instance, now);
            }
            for (Instance instance : went) {
                zone.inside.remove(instance);
                zone.changedAt.put(instance, now);
            }
            zone.changedAt.keySet().removeIf(instance -> !instance.isAlive());
            for (Instance instance : went) zone.left.fire(instance);
            for (Instance instance : came) zone.entered.fire(instance);
        }
    }

    private static boolean ready(Zone zone, Instance instance, double now) {
        Double last = zone.changedAt.get(instance);
        return last == null || now - last >= zone.cooldown;
    }

    private static Set<Instance> candidates(InstanceTree tree, ClassRegistry classes, Zone zone) {
        Set<Instance> out = new LinkedHashSet<>();
        if (zone.trackPlayers) {
            for (Character body : tree.ofClass(Classes.CHARACTER)) {
                if (body.worn()) out.add(body);
            }
        }
        if (!zone.trackTag.isEmpty()) {
            for (Instance tagged : tree.tagged(zone.trackTag)) {
                if (tagged instanceof Spatial) out.add(tagged);
            }
        }
        if (!zone.trackClass.isEmpty() && classes != null) {
            ClassDef<?> def = classes.find(zone.trackClass);
            if (def != null) {
                for (Instance instance : tree.ofClass(def)) {
                    if (instance instanceof Spatial) out.add(instance);
                }
            }
        }
        return out;
    }

    public static boolean contains(Zone zone, Vec3 point) {
        CFrame frame = Transforms.world(zone);
        Vec3 local = frame.pointToObject(point);
        Vec3 half = zone.size.mul(0.5);
        return switch (zone.shape) {
            case BOX -> Math.abs(local.x()) <= half.x() && Math.abs(local.y()) <= half.y() && Math.abs(local.z()) <= half.z();
            case SPHERE -> local.lengthSq() <= half.x() * half.x();
            case CYLINDER -> Math.abs(local.y()) <= half.y() && local.x() * local.x() + local.z() * local.z() <= half.x() * half.x();
            case POLYGON -> Math.abs(local.y()) <= half.y() && insideOutline(zone, local);
        };
    }

    private static boolean insideOutline(Zone zone, Vec3 local) {
        List<Vec3> points = new ArrayList<>();
        for (Instance child : zone.children()) {
            if (child instanceof Attachment point) points.add(point.cframe.position());
        }
        if (points.size() < 3) return false;
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(j);
            if ((a.z() > local.z()) != (b.z() > local.z())
                    && local.x() < (b.x() - a.x()) * (local.z() - a.z()) / (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static List<Zone> at(InstanceTree tree, Vec3 point) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : tree.ofClass(Classes.ZONE)) {
            if (zone.enabled && zone.isAlive() && contains(zone, point)) out.add(zone);
        }
        out.sort(Comparator.comparingInt((Zone zone) -> -zone.priority)
                .thenComparingDouble(zone -> zone.size.x() * zone.size.y() * zone.size.z()));
        return out;
    }
}
