package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import org.jspecify.annotations.Nullable;

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
