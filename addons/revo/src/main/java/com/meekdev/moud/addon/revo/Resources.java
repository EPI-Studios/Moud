package com.meekdev.moud.addon.revo;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class Resources {

    private Resources() {}

    static String text(String name) {
        try (InputStream in = Resources.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("the revo addon is missing " + name, e);
        }
    }

    static Map<String, String> table(String name) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text(name)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> out = new HashMap<>();
        for (String key : properties.stringPropertyNames()) out.put(key, properties.getProperty(key));
        return out;
    }

    static Set<String> words(String name) {
        return Set.of(text(name).trim().split("\\s+"));
    }
}
