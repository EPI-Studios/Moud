package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.core.clazz.ClassDef;
import java.util.HashMap;
import java.util.Map;

public final class ClassIcons {

    private static final String TABLE = "/assets/moud/editor/class-icons.json";
    private static final Map<String, EditorIcon> BY_CLASS = load();

    private ClassIcons() {}

    public static EditorIcon of(ClassDef<?> def) {
        for (ClassDef<?> at = def; at != null; at = at.parent()) {
            EditorIcon icon = BY_CLASS.get(at.name());
            if (icon != null) return icon;
        }
        return EditorIcon.NODE_3D;
    }

    private static Map<String, EditorIcon> load() {
        Map<String, EditorIcon> icons = new HashMap<>();
        for (Map.Entry<String, String> entry : EditorData.strings(TABLE).entrySet()) {
            icons.put(entry.getKey(), EditorIcon.valueOf(entry.getValue()));
        }
        return Map.copyOf(icons);
    }
}
