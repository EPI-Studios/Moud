package com.moud.client.imgui;

import imgui.extension.texteditor.TextEditorLanguageDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class TextEditorCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoudTextEditorCompat");

    private static final Method METHOD_SET_AUTO_INDENTATION = find("setAutoIndentation", boolean.class);
    private static final Method METHOD_SET_AUTO_IDENTATION = METHOD_SET_AUTO_INDENTATION == null
            ? find("setAutoIdentation", boolean.class)
            : null;

    private TextEditorCompat() {
    }

    private static Method find(String name, Class<?>... params) {
        try {
            Method method = TextEditorLanguageDefinition.class.getMethod(name, params);
            method.setAccessible(true);
            LOGGER.debug("TextEditorCompat resolved method {}", method);
            return method;
        } catch (NoSuchMethodException ignored) {
            LOGGER.debug("TextEditorCompat: method {} not present in this imgui build", name);
            return null;
        }
    }

    public static void setAutoIndentation(TextEditorLanguageDefinition definition, boolean enabled) {
        if (invoke(definition, enabled, METHOD_SET_AUTO_INDENTATION)) {
            return;
        }
        if (invoke(definition, enabled, METHOD_SET_AUTO_IDENTATION)) {
            return;
        }
        LOGGER.trace("TextEditorCompat: auto indentation setter not available, skipping");
    }

    private static boolean invoke(TextEditorLanguageDefinition definition, boolean enabled, Method method) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(definition, enabled);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("TextEditorCompat: {} invocation failed", method.getName(), t);
            return false;
        }
    }
}