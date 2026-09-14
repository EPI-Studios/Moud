package com.meekdev.moud.addon.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class Template {

    private final String text;

    private Template(String text) {
        this.text = text;
    }

    static Template load(String name) {
        return new Template(read(name));
    }

    String fill(String... pairs) {
        String out = text;
        for (int n = 0; n < pairs.length; n += 2) out = out.replace("{{" + pairs[n] + "}}", pairs[n + 1]);
        return out;
    }

    static String indent(String text, int spaces) {
        StringBuilder out = new StringBuilder();
        for (String line : text.lines().toList()) {
            if (!line.isEmpty()) out.append(" ".repeat(spaces));
            out.append(line).append('\n');
        }
        return out.toString();
    }

    static Map<String, String> table(String name) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(read(name)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> out = new HashMap<>();
        for (String key : properties.stringPropertyNames()) out.put(key, properties.getProperty(key));
        return out;
    }

    static Set<String> words(String name) {
        return Set.of(read(name).trim().split("\\s+"));
    }

    private static String read(String name) {
        try (InputStream in = Template.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException(name + " is missing from the java addon", e);
        }
    }
}
