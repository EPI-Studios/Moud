package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.instance.Instance;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ReferencePick {

    private static @Nullable Consumer<Instance> waiting;
    private static String what = "";

    private ReferencePick() {}

    public static void begin(String property, Consumer<Instance> chosen) {
        waiting = chosen;
        what = property;
    }

    public static boolean active() {
        return waiting != null;
    }

    public static String what() {
        return what;
    }

    public static void deliver(Instance picked) {
        Consumer<Instance> chosen = waiting;
        waiting = null;
        if (chosen != null && picked != null) chosen.accept(picked);
    }

    public static void cancel() {
        waiting = null;
    }
}
