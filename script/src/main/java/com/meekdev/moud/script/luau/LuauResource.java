package com.meekdev.moud.script.luau;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

final class LuauResource {

    private LuauResource() {}

    static String text(String name) {
        String text = textOrNull(name);
        if (text == null) throw new IllegalStateException(name + " is missing from the luau resources");
        return text;
    }

    static String textOrEmpty(String name) {
        String text = textOrNull(name);
        return text == null ? "" : text;
    }

    static Properties properties(String name) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text(name)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    private static String textOrNull(String name) {
        try (InputStream in = LuauResource.class.getResourceAsStream(name)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
