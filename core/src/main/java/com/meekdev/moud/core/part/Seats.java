package com.meekdev.moud.core.part;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class Seats {

    public static final double COOLDOWN = 1.0;

    private static final PropertyDef OCCUPANT = Classes.SEAT.property("occupant");
    private static final PropertyDef SIT = Classes.HUMANOID.property("sit");

    private static final Map<Humanoid, Seat> SEATED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Humanoid, Double> WAITING = Collections.synchronizedMap(new WeakHashMap<>());

    private Seats() {}

    public static boolean canSit(Seat seat, Humanoid living) {
        return !seat.disabled && seat.isAlive() && living.isAlive()
                && living.health > 0 && living.stateEnabled(HumanoidState.SEATED)
                && (seat.occupant == null || seat.occupant == living)
                && living.parent() instanceof Character;
    }

    public static boolean sit(Seat seat, Humanoid living) {
        if (seat.occupant == living) return true;
        if (!canSit(seat, living)) return false;
        Seat before = of(living);
        if (before != null) stand(before);
        Instances.setObj(seat, OCCUPANT, living);
        Instances.setBool(living, SIT, true);
        SEATED.put(living, seat);
        return true;
    }

    public static void stand(Seat seat) {
        Humanoid living = sitter(seat);
        if (seat.isAlive()) Instances.setObj(seat, OCCUPANT, null);
        if (living != null) release(living);
    }

    public static Seat of(Humanoid living) {
        Seat seat = SEATED.get(living);
        return seat != null && seat.isAlive() ? seat : null;
    }

    public static Humanoid sitter(Seat seat) {
        return seat.occupant instanceof Humanoid living ? living : null;
    }

    public static Character body(Seat seat) {
        Humanoid living = sitter(seat);
        return living != null && living.parent() instanceof Character body ? body : null;
    }

    public static CFrame frame(Seat seat) {
        CFrame world = Transforms.world(seat);
        return world.withPosition(world.pointToWorld(new Vector3(0, seat.size.y() * 0.5, 0)));
    }

    public static boolean waiting(Humanoid living) {
        return WAITING.containsKey(living);
    }

    public static void tick(InstanceTree tree, double dt) {
        WAITING.entrySet().removeIf(entry -> {
            double left = entry.getValue() - dt;
            entry.setValue(left);
            return left <= 0;
        });
        for (Map.Entry<Humanoid, Seat> entry : new HashMap<>(SEATED).entrySet()) {
            Humanoid living = entry.getKey();
            Seat seat = entry.getValue();
            if (living.tree() != tree && seat.tree() != tree) continue;
            if (seat.isAlive() && !seat.disabled && living.isAlive() && living.sit && living.health > 0
                    && living.parent() instanceof Character body && body.isAlive()) {
                continue;
            }
            if (seat.isAlive()) Instances.setObj(seat, OCCUPANT, null);
            release(living);
        }
    }

    public static void clear() {
        SEATED.clear();
        WAITING.clear();
    }

    private static void release(Humanoid living) {
        SEATED.remove(living);
        if (living.isAlive() && living.sit) Instances.setBool(living, SIT, false);
        WAITING.put(living, COOLDOWN);
    }
}
