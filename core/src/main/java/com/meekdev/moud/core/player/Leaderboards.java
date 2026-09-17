package com.meekdev.moud.core.player;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.value.BoolValue;
import com.meekdev.moud.core.value.NumberValue;
import com.meekdev.moud.core.value.StringValue;
import com.meekdev.moud.core.value.Value;
import java.util.ArrayList;
import java.util.List;

public final class Leaderboards {

    public static final String NAME = "Leaderboard";

    private Leaderboards() {}

    public static Leaderboard board(Instance world) {
        if (world.child(NAME) instanceof Leaderboard board) return board;
        return Instances.create(Classes.LEADERBOARD, world, NAME);
    }

    public static Leaderstats of(Instance world, String owner, String name) {
        Leaderstats found = find(world, owner);
        if (found != null) {
            if (!found.name().equals(name)) Instances.rename(found, name);
            return found;
        }
        return Instances.create(Classes.LEADERSTATS, board(world), name, stats -> stats.owner = owner);
    }

    public static Leaderstats find(Instance world, String owner) {
        if (owner.isEmpty() || !(world.child(NAME) instanceof Leaderboard board)) return null;
        for (Instance child : board.children()) {
            if (child instanceof Leaderstats stats && stats.owner.equals(owner)) return stats;
        }
        return null;
    }

    public static void forget(Instance world, String owner) {
        Leaderstats stats = find(world, owner);
        if (stats != null) Instances.destroy(stats);
    }

    public static List<Value> values(Leaderstats stats) {
        List<Value> values = new ArrayList<>();
        for (Instance child : stats.children()) {
            if (child instanceof Value value) values.add(value);
        }
        return values;
    }

    public static String text(Value value) {
        return switch (value) {
            case NumberValue number -> number(number.value);
            case StringValue string -> string.value;
            case BoolValue flag -> flag.value ? "yes" : "no";
            default -> "";
        };
    }

    public static String columns(Leaderstats stats) {
        StringBuilder out = new StringBuilder();
        for (Value value : values(stats)) {
            out.append("  ").append(value.name()).append(' ').append(text(value));
        }
        return out.toString();
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }

    public static double leading(Leaderstats stats) {
        for (Instance child : stats.children()) {
            if (child instanceof NumberValue number) return number.value;
        }
        return Double.NEGATIVE_INFINITY;
    }
}
