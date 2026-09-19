package com.meekdev.moud.mod.client.editor.plugin;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.script.api.PluginRef;
import com.meekdev.moud.script.host.HostError;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class PluginDesk implements PluginRef {

    private final SceneDocument document;
    private final PluginEdits edits;
    private final PluginSettings settings;
    private final Supplier<Mouse> mouse;

    PluginDesk(SceneDocument document, PluginEdits edits, PluginSettings settings, Supplier<Mouse> mouse) {
        this.document = document;
        this.edits = edits;
        this.settings = settings;
        this.mouse = mouse;
    }

    @Override
    public List<Instance> selection() {
        List<Instance> out = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance instance = document.find(id);
            if (instance != null) out.add(instance);
        }
        return out;
    }

    @Override
    public void select(List<Instance> chosen) {
        List<Integer> ids = new ArrayList<>();
        for (Instance instance : chosen) {
            if (edits.pending(instance)) edits.select(instance);
            else if (instance != document.world() && document.editable(instance)) ids.add(instance.id());
            else throw new HostError("cannot select %s, it is not part of the scene", instance.name());
        }
        document.selection().set(ids);
    }

    @Override
    public Object setting(String plugin, String key) {
        return settings.get(plugin, key);
    }

    @Override
    public void setting(String plugin, String key, Object value) {
        settings.set(plugin, key, value);
    }

    @Override
    public Mouse mouse() {
        return mouse.get();
    }

    @Override
    public void record(String label, Runnable changes) {
        edits.record(label, changes);
    }
}
