package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.instance.Instance;
import java.util.ArrayList;
import java.util.List;

public record Destroy(List<InstanceRef> roots, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        for (InstanceRef root : roots) {
            if (!root.resolved()) throw new IllegalStateException("still waiting for the server");
        }
        document.destroy(roots);
    }

    @Override
    public Edit invert(SceneDocument document) {
        List<Instance> gone = new ArrayList<>();
        for (InstanceRef root : roots) {
            Instance instance = document.find(root);
            if (instance == null || instance.parent() == null) throw new IllegalStateException("that instance is gone");
            gone.add(instance);
        }
        List<Edit> restores = new ArrayList<>();
        for (Instance instance : document.inTreeOrder(gone)) {
            List<InstanceRef> all = new ArrayList<>();
            document.collect(instance, all);
            restores.add(new Paste(document.snapshot(List.of(instance)), document.ref(instance.parent().id()),
                    new ArrayList<>(List.of(document.ref(instance.id()))), all, false, "Restore",
                    document.relinks(List.of(instance), 0, true), document.spotBefore(instance)));
        }
        return restores.size() == 1 ? restores.getFirst() : new Batch("Restore", restores);
    }
}
