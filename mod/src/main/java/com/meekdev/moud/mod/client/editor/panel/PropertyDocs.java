package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.mod.client.editor.style.EditorData;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class PropertyDocs {

    private static final String TABLE = "/assets/moud/editor/property-docs.json";
    private static final Map<String, String> DOCS = EditorData.strings(TABLE);

    private PropertyDocs() {}

    static @Nullable String of(ClassDef<?> owner, String property) {
        for (ClassDef<?> at = owner; at != null; at = at.parent()) {
            String doc = DOCS.get(at.name() + "." + property);
            if (doc != null) return doc;
        }
        return DOCS.get(property);
    }
}
