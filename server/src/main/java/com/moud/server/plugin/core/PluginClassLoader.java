package com.moud.server.plugin.core;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Load first from the plugin JAR, then fall back to parent (core + JDK).
 */
public class PluginClassLoader extends URLClassLoader {
    public PluginClassLoader(URL jar, ClassLoader parent) {
        super(new URL[] { jar }, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1) JDK/JNI natif : always parent-first (otherwise bug)
        if (name.startsWith("java.") || name.startsWith("jdk.")) {
            return super.loadClass(name, resolve);
        }
        // 2) Try first in the plugin JAR
        try {
            Class<?> c = findClass(name);
            if (resolve) resolveClass(c);
            return c;
        } catch (ClassNotFoundException ignored) {
            // 3) else parent
            return super.loadClass(name, resolve);
        }
    }
}
