package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.moud.script.host.java.MixinHook;
import com.meekdev.moud.script.host.java.Mixins;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class MixinInspector extends Inspector {

    private record Sample(long at, long calls, long nanos, double perSecond, double msPerSecond) {}

    private final Map<MixinHook, Sample> samples = new WeakHashMap<>();

    public MixinInspector() {
        super("moud", "mixins", true);
    }

    @Override
    public void render() {
        List<MixinHook> hooks = Mixins.live();
        if (hooks.isEmpty()) {
            ImGui.textDisabled("no hooks are on");
            return;
        }
        ImGui.text(hooks.size() + " hooks");
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp;
        if (!ImGui.beginTable("moud.mixins", 7, flags)) return;
        ImGui.tableSetupColumn("on");
        ImGui.tableSetupColumn("side");
        ImGui.tableSetupColumn("hook");
        ImGui.tableSetupColumn("file");
        ImGui.tableSetupColumn("calls/s");
        ImGui.tableSetupColumn("ms/s");
        ImGui.tableSetupColumn("errors");
        ImGui.tableHeadersRow();
        long now = System.nanoTime();
        for (MixinHook hook : hooks) {
            Sample sample = sample(hook, now);
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            if (ImGui.checkbox("##" + System.identityHashCode(hook), hook.enabled())) hook.enabled(!hook.enabled());
            ImGui.tableNextColumn();
            ImGui.text(hook.client() ? "client" : "server");
            ImGui.tableNextColumn();
            ImGui.text(hook.label());
            ImGui.tableNextColumn();
            ImGui.textDisabled(hook.file() == null ? "script" : hook.file());
            ImGui.tableNextColumn();
            ImGui.text(String.format("%.0f", sample.perSecond()));
            ImGui.tableNextColumn();
            ImGui.text(String.format("%.2f", sample.msPerSecond()));
            ImGui.tableNextColumn();
            if (hook.errors() > 0) ImGui.textColored(1f, 0.4f, 0.4f, 1f, String.valueOf(hook.errors()));
            else ImGui.textDisabled("0");
        }
        ImGui.endTable();
    }

    private Sample sample(MixinHook hook, long now) {
        Sample last = samples.get(hook);
        if (last == null) {
            Sample first = new Sample(now, hook.calls(), hook.nanos(), 0, 0);
            samples.put(hook, first);
            return first;
        }
        double seconds = (now - last.at()) / 1e9;
        if (seconds < 1) return last;
        Sample next = new Sample(now, hook.calls(), hook.nanos(), (hook.calls() - last.calls()) / seconds,
                (hook.nanos() - last.nanos()) / 1e6 / seconds);
        samples.put(hook, next);
        return next;
    }
}
