package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;

public final class Selection {

    private final Set<Integer> ids = new LinkedHashSet<>();

    public void select(int id) {
        ids.clear();
        ids.add(id);
    }

    public void toggle(int id) {
        if (!ids.remove(id)) ids.add(id);
    }

    public void add(int id) {
        ids.remove(id);
        ids.add(id);
    }

    public void clear() {
        ids.clear();
    }

    public boolean isSelected(int id) {
        return ids.contains(id);
    }

    public int count() {
        return ids.size();
    }

    public OptionalInt primary() {
        int last = 0;
        boolean any = false;
        for (int id : ids) {
            last = id;
            any = true;
        }
        return any ? OptionalInt.of(last) : OptionalInt.empty();
    }

    public List<Integer> all() {
        return new ArrayList<>(ids);
    }

    void keep(IntPredicate alive) {
        ids.removeIf(id -> !alive.test(id));
    }
}
