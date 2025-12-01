package com.moud.server.plugin.core;

import com.moud.server.plugin.PluginLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PluginDescription {
    public final String name;
    public final String id;
    public final String mainClass;
    public final String version;
    public final String apiVersion;
    public final List<String> depends;
    public final String description;

    private static final Pattern DEP_PATTERN =
            Pattern.compile("^\\s*([\\w-]+)(?:\\s*>=\\s*([\\d.]+))?\\s*$");

    @SuppressWarnings("unchecked")
    public PluginDescription(InputStream yamlStream) {
        Map<String, Object> map = new Yaml().load(yamlStream);
        name       = (String) map.get("name");
        id         = (String) map.get("id");
        mainClass  = (String) map.get("main-class");
        version    = (String) map.getOrDefault("version", "0.0.0");
        apiVersion = (String) map.getOrDefault("api-version", PluginLoader.API_VERSION);
        depends    = (List<String>) map.getOrDefault("depends", List.of());
        description = (String) map.getOrDefault("description", "No description provided");
    }

    /** Extrait simplement le nom d’un élément depends ("logger >=1.1" -> "logger"). */
    public static String depName(String raw) {
        var m = DEP_PATTERN.matcher(raw);
        return m.matches() ? m.group(1) : raw.trim();
    }
}
