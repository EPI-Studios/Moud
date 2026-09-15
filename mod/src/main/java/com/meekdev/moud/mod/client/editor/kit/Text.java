package com.meekdev.moud.mod.client.editor.kit;

import java.text.MessageFormat;
import java.util.Locale;

public final class Text {

    private static final String IDENTIFIER_SEPARATOR = "###";

    private Text() {}

    public static String of(String pattern, Object... arguments) {
        return new MessageFormat(pattern, Locale.ROOT).format(arguments);
    }

    public static String label(String text, String identifier) {
        return text + IDENTIFIER_SEPARATOR + identifier;
    }
}
