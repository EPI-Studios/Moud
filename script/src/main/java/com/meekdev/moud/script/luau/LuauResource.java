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

    static byte[] bytesOrNull(String name) {
        try (InputStream in = LuauResource.class.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String textOrNull(String name) {
        byte[] bytes = bytesOrNull(name);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
