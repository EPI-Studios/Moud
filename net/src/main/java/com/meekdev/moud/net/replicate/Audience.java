package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

public final class Audience {

    public static final double RADIUS = 256;

    private final String player;

    private InstanceTree tree;

    private final BitSet has = new BitSet();

    public Audience(String player) {
        this.player = player;
    }

    public String player() {
        return player;
    }

    public boolean holds(int id) {
        return has.get(id);
    }

    public void drain(InstanceTree from, List<Change> tick, Vector3 focus, double radius,
                      Consumer<Change> out) {
        if (from != tree) {
            tree = from;
            has.clear();
            out.accept(new Change.Reset());
        }
        if (tree == null) return;

        has.set(tree.root().id());

        for (Instance top : tree.root().children()) {
            boolean want = relevant(top, focus, radius);
            if (want == has.get(top.id())) continue;
            if (want) {
                baseline(top, out);
            } else {
                out.accept(new Change.Destroyed(top.id()));
                forget(top);
            }
        }

        for (int n = 0; n < tick.size(); n++) {
            switch (tick.get(n)) {
                case Change.Reset ignored -> { }
                case Change.Created fresh -> {
                    if (!has.get(fresh.parent())) continue;
                    Instance made = tree.byId(fresh.id());
                    if (made != null && shouldReceive(made, focus, radius)) baseline(made, out);
                }
                case Change.Destroyed gone -> {
                    if (!has.get(gone.id())) continue;
                    out.accept(gone);
                    has.clear(gone.id());
                }
                case Change.Moved moved -> {
                    if (!has.get(moved.id())) {
                        Instance came = tree.byId(moved.id());
                        if (has.get(moved.parent()) && came != null
                                && shouldReceive(came, focus, radius)) {
                            baseline(came, out);
                        }
                        continue;
                    }
                    if (has.get(moved.parent())) {
                        out.accept(moved);
                    } else {
                        out.accept(new Change.Destroyed(moved.id()));
                        Instance left = tree.byId(moved.id());
                        if (left != null) forget(left);
                        else has.clear(moved.id());
                    }
                }
                case Change.Wrote wrote -> {
                    if (has.get(wrote.id())) out.accept(wrote);
                }
                case Change.Tagged tagged -> {
                    if (has.get(tagged.id())) out.accept(tagged);
                }
            }
        }
    }

    private void baseline(Instance top, Consumer<Change> out) {
        if (top.parent() == null || has.get(top.id())) return;
        out.accept(new Change.Created(top.id(), top.def().name(), top.parent().id(), top.name()));
        has.set(top.id());
        long skip = top.def().unreplicated() | top.externalProperties();
        for (PropertyDef property : top.def().properties()) {
            if ((skip & (1L << property.index())) != 0) continue;
            out.accept(new Change.Wrote(top.id(), property.index(), Recorder.read(top, property)));
        }
        for (String tag : top.tags()) out.accept(new Change.Tagged(top.id(), tag, true));
        for (Instance child : top.children()) baseline(child, out);
    }

    private void forget(Instance top) {
        has.clear(top.id());
        for (Instance child : top.children()) forget(child);
    }

    private boolean shouldReceive(Instance made, Vector3 focus, double radius) {
        return made.parent() != tree.root() || relevant(made, focus, radius);
    }

    private static boolean relevant(Instance top, Vector3 focus, double radius) {
        if (!(top instanceof Spatial spatial)) return true;
        if (spatial.alwaysRelevant) return true;
        if (focus == null) return true;
        Vector3 where = Transforms.world(top).position();
        return where.sub(focus).lengthSq() <= radius * radius;
    }
}
