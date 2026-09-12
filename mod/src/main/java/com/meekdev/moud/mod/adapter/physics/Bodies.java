package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import org.jspecify.annotations.Nullable;

// the body a player wears, from either side
//
// the server holds a map of its own, keyed on the uuid it bound. a mixin does not: it runs on
// whichever side it was loaded on, and the client's answer is in the mirror. so the one thing both
// sides can agree on is the owner written on the body
public final class Bodies {

    private Bodies() {}

    public static @Nullable Character of(@Nullable InstanceTree tree, String owner) {
        if (tree == null || owner.isEmpty()) return null;
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character && owner.equals(character.owner)) {
                return character;
            }
        }
        return null;
    }
}
