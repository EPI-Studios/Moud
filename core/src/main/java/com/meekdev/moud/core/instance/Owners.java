package com.meekdev.moud.core.instance;

// who may write a thing
//
// one question with one answer, in one place, because it is asked from the binding on every write a
// place makes on a client. the answer is the nearest owner up the chain, so a branch is owned as a
// branch: hand somebody a cart and they own what is standing on it
public final class Owners {

    private Owners() {}

    // empty means the server owns it, which is almost everything
    public static String of(Instance instance) {
        for (Instance at = instance; at != null; at = at.parent()) {
            if (at instanceof Spatial spatial && !spatial.owner.isEmpty()) return spatial.owner;
        }
        return "";
    }

    public static boolean owns(String player, Instance instance) {
        return !player.isEmpty() && player.equals(of(instance));
    }
}
