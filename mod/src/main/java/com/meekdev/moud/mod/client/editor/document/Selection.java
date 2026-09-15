package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;

public final class Selection {

    private static final int HISTORY = 64;

    private final Set<Integer> ids = new LinkedHashSet<>();
    private final Deque<List<Integer>> back = new ArrayDeque<>();
    private final Deque<List<Integer>> forward = new ArrayDeque<>();
    private List<Integer> recorded = List.of();
    private boolean travelling;

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

    public void set(List<Integer> chosen) {
        ids.clear();
        ids.addAll(chosen);
    }

    void keep(IntPredicate alive) {
        ids.removeIf(id -> !alive.test(id));
        back.forEach(list -> list.removeIf(id -> !alive.test(id)));
        forward.forEach(list -> list.removeIf(id -> !alive.test(id)));
    }

    void record() {
        List<Integer> now = all();
        if (now.equals(recorded)) return;
        if (!travelling) {
            back.push(new ArrayList<>(recorded));
            while (back.size() > HISTORY) back.pollLast();
            forward.clear();
        }
        travelling = false;
        recorded = now;
    }

    public boolean canGoBack() {
        return !back.isEmpty();
    }

    public boolean canGoForward() {
        return !forward.isEmpty();
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(all());
        set(back.pop());
        travelling = true;
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(all());
        set(forward.pop());
        travelling = true;
    }
}
