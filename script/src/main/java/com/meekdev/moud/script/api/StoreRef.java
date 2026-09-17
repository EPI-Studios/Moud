package com.meekdev.moud.script.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

public interface StoreRef {

    String get(String store, String key);

    void set(String store, String key, String json);

    String update(String store, String key, UnaryOperator<String> change);

    List<String> keys(String store, String prefix, int limit);

    boolean lease(String store, String key, String owner, double seconds);

    void release(String store, String key, String owner);

    String owner();

    record Ranked(String key, double value) {}

    default List<Ranked> sorted(String store, boolean ascending, int limit, double min, double max) {
        List<Ranked> all = new ArrayList<>();
        for (String key : keys(store, "", Integer.MAX_VALUE)) {
            String json = get(store, key);
            if (json == null) continue;
            try {
                double value = Double.parseDouble(json);
                if (value >= min && value <= max) all.add(new Ranked(key, value));
            } catch (NumberFormatException ignored) {
            }
        }
        Comparator<Ranked> order = Comparator.comparingDouble(Ranked::value);
        all.sort(ascending ? order.thenComparing(Ranked::key) : order.reversed().thenComparing(Ranked::key));
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
