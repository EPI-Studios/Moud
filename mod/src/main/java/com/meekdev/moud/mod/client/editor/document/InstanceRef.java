package com.meekdev.moud.mod.client.editor.document;

public final class InstanceRef {

    int id;

    InstanceRef(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public boolean resolved() {
        return id != 0;
    }
}
