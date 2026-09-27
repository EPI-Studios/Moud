package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.instance.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record ExplorerFilter(String text, List<String> classes) {

    static final ExplorerFilter NONE = new ExplorerFilter("", List.of());

    static ExplorerFilter parse(String raw) {
        List<String> words = new ArrayList<>();
        List<String> classes = new ArrayList<>();
        for (String token : raw.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (token.startsWith("t:") || token.startsWith("c:")) {
                for (String name : token.substring(2).split(",")) {
                    if (!name.isEmpty()) classes.add(name);
                }
            } else if (!token.isEmpty()) {
                words.add(token);
            }
        }
        return new ExplorerFilter(String.join(" ", words), classes);
    }

    boolean empty() {
        return text.isEmpty() && classes.isEmpty();
    }

    boolean matches(Instance instance) {
        if (!text.isEmpty() && !instance.name().toLowerCase(Locale.ROOT).contains(text)) return false;
        if (classes.isEmpty()) return true;
        for (String wanted : classes) {
            if (instance.def().name().toLowerCase(Locale.ROOT).contains(wanted)) return true;
            for (ClassDef<?> def = instance.def(); def != null; def = def.parent()) {
                if (def.name().equalsIgnoreCase(wanted)) return true;
            }
        }
        return false;
    }
}
