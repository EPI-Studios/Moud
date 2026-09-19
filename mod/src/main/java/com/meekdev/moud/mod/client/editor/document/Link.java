package com.meekdev.moud.mod.client.editor.document;

import java.util.List;

public record Link(List<InstanceRef> target, int property, List<InstanceRef> value, boolean live, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        if (live) document.afterPastes(() -> document.write(only(target), property, only(value).id()));
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Link(target, property, value, !live, label);
    }

    private static InstanceRef only(List<InstanceRef> refs) {
        if (refs.isEmpty()) throw new IllegalStateException("the new instance never arrived");
        return refs.getFirst();
    }
}
