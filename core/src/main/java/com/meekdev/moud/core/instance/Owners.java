package com.meekdev.moud.core.instance;

public final class Owners {

    private Owners() {}

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
