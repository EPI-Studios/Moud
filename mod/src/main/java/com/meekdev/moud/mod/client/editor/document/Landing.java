package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class Landing {

    private record Waiting(Set<Integer> tokens, Runnable run) {}

    private final List<Waiting> waiting = new ArrayList<>();

    void after(Set<Integer> pending, Runnable run) {
        if (pending.isEmpty()) run.run();
        else waiting.add(new Waiting(Set.copyOf(pending), run));
    }

    void landed(Set<Integer> pending, Consumer<RuntimeException> failed) {
        Iterator<Waiting> each = waiting.iterator();
        List<Runnable> ready = new ArrayList<>();
        while (each.hasNext()) {
            Waiting one = each.next();
            if (!Collections.disjoint(one.tokens(), pending)) continue;
            each.remove();
            ready.add(one.run());
        }
        for (Runnable run : ready) {
            try {
                run.run();
            } catch (RuntimeException e) {
                failed.accept(e);
            }
        }
    }

    void clear() {
        waiting.clear();
    }
}
