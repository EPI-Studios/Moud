package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.chat.ChatAnimation;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import java.util.Map;

public final class Looks {

    private Looks() {}

    static double number(Map<String, Object> look, String key, double fallback) {
        return look.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    static boolean bool(Map<String, Object> look, String key, boolean fallback) {
        return look.get(key) instanceof Boolean b ? b : fallback;
    }

    static String string(Map<String, Object> look, String key, String fallback) {
        return look.get(key) instanceof String s ? s : fallback;
    }

    static Color color(Map<String, Object> look, String key, Color fallback) {
        Object value = look.get(key);
        if (value instanceof Color c) return c;
        if (value instanceof String s) {
            Color parsed = RichText.color(s);
            if (parsed != null) return parsed;
        }
        return fallback;
    }

    static ChatAnimation animation(Map<String, Object> look, String key, ChatAnimation fallback) {
        if (!(look.get(key) instanceof String name)) return fallback;
        for (ChatAnimation one : ChatAnimation.values()) {
            if (Enums.name(one).equals(name)) return one;
        }
        return fallback;
    }
}
