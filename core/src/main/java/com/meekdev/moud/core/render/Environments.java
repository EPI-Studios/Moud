package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;

public final class Environments {

    private Environments() {}

    public static Lighting lighting(InstanceTree tree) {
        if (tree == null) return null;
        for (Lighting lighting : tree.ofClass(Classes.LIGHTING)) {
            if (!Instance.outOfWorld(lighting)) return lighting;
        }
        return null;
    }

    public static Sky sky(InstanceTree tree) {
        return under(tree, Classes.SKY);
    }

    public static Atmosphere atmosphere(InstanceTree tree) {
        return under(tree, Classes.ATMOSPHERE);
    }

    public static Clouds clouds(InstanceTree tree) {
        return under(tree, Classes.CLOUDS);
    }

    private static <T extends Instance> T under(InstanceTree tree, ClassDef<T> def) {
        Lighting lighting = lighting(tree);
        if (lighting == null) return null;
        for (T found : tree.ofClass(def)) {
            for (Instance at = found.parent(); at != null; at = at.parent()) {
                if (at == lighting) return found;
            }
        }
        return null;
    }
}
