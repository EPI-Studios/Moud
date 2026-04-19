package com.moud.server.minestom.scripting.luau;

import com.moud.core.scripts.luau.LuauExport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServerLuauExportRegistry {

    private static final List<Class<?>> CLASSES = new ArrayList<>();

    private ServerLuauExportRegistry() {}

    public static synchronized void register(Class<?> cls) {
        if (cls == null || CLASSES.contains(cls)) return;
        if (!cls.isAnnotationPresent(LuauExport.class)) {
            throw new IllegalArgumentException(cls.getName() + " is not @LuauExport");
        }
        CLASSES.add(cls);
    }

    public static synchronized List<Class<?>> classes() {
        return Collections.unmodifiableList(new ArrayList<>(CLASSES));
    }
}
