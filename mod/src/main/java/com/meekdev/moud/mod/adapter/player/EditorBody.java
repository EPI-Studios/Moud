package com.meekdev.moud.mod.adapter.player;

import net.minecraft.world.entity.Entity;

public interface EditorBody {

    boolean moud$editing();

    void moud$setEditing(boolean editing);

    static boolean editing(Entity entity) {
        return entity instanceof EditorBody body && body.moud$editing();
    }

    static void set(Entity entity, boolean editing) {
        if (entity instanceof EditorBody body) body.moud$setEditing(editing);
    }
}
