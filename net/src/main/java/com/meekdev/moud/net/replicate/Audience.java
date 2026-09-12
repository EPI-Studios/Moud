package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vec3;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

// one player's copy: what they have, and what they are about to be told
//
// the recorder says what changed in the tree. that is one answer for everybody, and it is not what
// goes on any one connection: a player holds the part of the place they are near (§10.1), so the same
// tick is a different packet per player, and a player who just arrived holds nothing and needs all of
// it. both of those are this class, and neither is the recorder's business
//
// interest is decided for a whole branch at a time, off the top of it, and the branch goes with it.
// that is not a simplification -- our tree *is* the transform hierarchy, so a child whose parent is
// not in your copy has nothing to hang off and no place to be. roblox reaches the same rule from the
// other end and calls it an atomic model
public final class Audience {

    // how far a player holds the place around them, in metres
    //
    // a starting number rather than a tuned one, and a place changes it. it is roughly the game's own
    // far entity tracking range, which is the distance at which it stops telling you about things --
    // so a place that feels right in vanilla terms feels right at this
    public static final double RADIUS = 256;

    private final String player;

    private InstanceTree tree;

    // which ids this player's copy holds. a bitset because the question is asked for every change of
    // every tick and the answer is one bit
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

    // what this player is told this tick: the tick's own changes, minus what they cannot see, plus
    // everything they can see for the first time
    public void drain(InstanceTree from, List<Change> tick, Vec3 focus, double radius,
                      Consumer<Change> out) {
        if (from != tree) {
            tree = from;
            has.clear();
            // a place that restarted is not a place that changed. the copy goes, whole
            out.accept(new Change.Reset());
        }
        if (tree == null) return;

        // the root is the one instance nobody is told about: the copy is built around its own
        has.set(tree.root().id());

        // first, what left. a branch that walked out of range is gone from this copy the same way a
        // destroyed one is -- the client cannot tell the two apart and has no reason to
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

        // then the tick, which is already in the right order: structure before properties
        for (int n = 0; n < tick.size(); n++) {
            switch (tick.get(n)) {
                // the recorder's own reset is the authority's, not a player's. a player is reset by
                // the tree changing under them, which is the branch above
                case Change.Reset ignored -> { }
                case Change.Created fresh -> {
                    if (!has.get(fresh.parent())) continue;
                    Instance made = tree.byId(fresh.id());
                    // the root is held by everybody, so a branch hanging straight off it is the one
                    // case where holding the parent says nothing about holding the child: that is
                    // exactly where interest is decided
                    if (made != null && wanted(made, focus, radius)) baseline(made, out);
                }
                case Change.Destroyed gone -> {
                    if (!has.get(gone.id())) continue;
                    out.accept(gone);
                    has.clear(gone.id());
                }
                case Change.Moved moved -> {
                    if (!has.get(moved.id())) {
                        // it moved into something this player holds, so now they hold it
                        Instance came = tree.byId(moved.id());
                        if (has.get(moved.parent()) && came != null
                                && wanted(came, focus, radius)) {
                            baseline(came, out);
                        }
                        continue;
                    }
                    if (has.get(moved.parent())) {
                        out.accept(moved);
                    } else {
                        // and out of everything they hold, which is a leave and not a move
                        out.accept(new Change.Destroyed(moved.id()));
                        Instance left = tree.byId(moved.id());
                        if (left != null) forget(left);
                        else has.clear(moved.id());
                    }
                }
                case Change.Wrote wrote -> {
                    if (has.get(wrote.id())) out.accept(wrote);
                }
            }
        }
    }

    // a branch, parents before children, with every property each one has. the same shape the tick
    // stream has, so the far side applies it through exactly the same path
    private void baseline(Instance top, Consumer<Change> out) {
        if (top.parent() == null || has.get(top.id())) return;
        out.accept(new Change.Created(top.id(), top.def().name(), top.parent().id(), top.name()));
        has.set(top.id());
        long skip = top.def().unreplicated() | top.fromElsewhere();
        for (PropertyDef property : top.def().properties()) {
            if ((skip & (1L << property.index())) != 0) continue;
            out.accept(new Change.Wrote(top.id(), property.index(), Recorder.read(top, property)));
        }
        for (Instance child : top.children()) baseline(child, out);
    }

    // the branch is still in the tree, it is only out of range -- so the bits have to be cleared by
    // walking it. leaving them set is a branch that can never come back: it would look held
    private void forget(Instance top) {
        has.clear(top.id());
        for (Instance child : top.children()) forget(child);
    }

    // whether this player holds it, asked of something whose parent they already hold. a branch off
    // the root is decided on its own; anything deeper goes with the branch it is in
    private boolean wanted(Instance made, Vec3 focus, double radius) {
        return made.parent() != tree.root() || relevant(made, focus, radius);
    }

    // one branch, one answer
    private static boolean relevant(Instance top, Vec3 focus, double radius) {
        // something with no position is not something distance is a question about: a channel, a
        // shared value, a folder of them. those are the place itself and everybody holds them
        if (!(top instanceof Spatial spatial)) return true;
        if (spatial.alwaysRelevant) return true;
        if (focus == null) return true;
        Vec3 where = Transforms.world(top).position();
        return where.sub(focus).lengthSq() <= radius * radius;
    }
}
