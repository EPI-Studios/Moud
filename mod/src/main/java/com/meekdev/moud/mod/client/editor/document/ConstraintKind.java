package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;

public enum ConstraintKind {
    WELD(Classes.WELD_CONSTRAINT, "Keeps two parts together as they are"),
    HINGE(Classes.HINGE_CONSTRAINT, "Lets the second part spin around the first point's face, like a wheel or a door"),
    PRISMATIC(Classes.PRISMATIC_CONSTRAINT, "Lets the second part slide along the first point's face direction"),
    BALL_SOCKET(Classes.BALL_SOCKET_CONSTRAINT, "Lets the second part turn freely around the first point"),
    ROPE(Classes.ROPE_CONSTRAINT, "Keeps the two points at most as far apart as they are now"),
    SPRING(Classes.SPRING_CONSTRAINT, "Pulls or pushes the two points back to the distance they are at now"),
    ROD(Classes.ROD_CONSTRAINT, "Keeps the two points exactly as far apart as they are now");

    private final ClassDef<? extends Instance> def;
    private final String hint;

    ConstraintKind(ClassDef<? extends Instance> def, String hint) {
        this.def = def;
        this.hint = hint;
    }

    public ClassDef<? extends Instance> def() {
        return def;
    }

    public String label() {
        return def.name();
    }

    public String hint() {
        return hint;
    }

    public boolean attached() {
        return this != WELD;
    }

    public boolean aligned() {
        return this == HINGE || this == PRISMATIC || this == BALL_SOCKET;
    }
}
