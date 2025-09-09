package com.moud.client.ui.theme;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThemeManager {
    private final Map<String, Theme> themes = new ConcurrentHashMap<>();
    private Theme currentTheme;

    public ThemeManager() {
        loadDefaultThemes();
        currentTheme = themes.get("default");
    }

    private void loadDefaultThemes() {
        Theme defaultTheme = new Theme("default");
        defaultTheme.setColor("primary", "#4A90E2");
        defaultTheme.setColor("secondary", "#7B68EE");
        defaultTheme.setColor("background", "#FFFFFF");
        defaultTheme.setColor("text", "#000000");
        defaultTheme.setColor("border", "#CCCCCC");
        themes.put("default", defaultTheme);

        Theme darkTheme = new Theme("dark");
        darkTheme.setColor("primary", "#5DADE2");
        darkTheme.setColor("secondary", "#BB86FC");
        darkTheme.setColor("background", "#121212");
        darkTheme.setColor("text", "#FFFFFF");
        darkTheme.setColor("border", "#444444");
        themes.put("dark", darkTheme);
    }

    public void setTheme(String themeName) {
        Theme theme = themes.get(themeName);
        if (theme != null) {
            currentTheme = theme;
        }
    }

    public String getColor(String colorName) {
        return currentTheme != null ? currentTheme.getColor(colorName) : "#FFFFFF";
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void addTheme(Theme theme) {
        themes.put(theme.getName(), theme);
    }

    public static class Theme {
        private final String name;
        private final Map<String, String> colors = new ConcurrentHashMap<>();

        public Theme(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setColor(String colorName, String colorValue) {
            colors.put(colorName, colorValue);
        }

        public String getColor(String colorName) {
            return colors.getOrDefault(colorName, "#FFFFFF");
        }
    }
}