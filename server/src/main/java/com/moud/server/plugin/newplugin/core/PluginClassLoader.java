package com.moud.server.plugin.newplugin.core;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Charge d'abord les classes situées dans le JAR du plug-in,
 * puis se rabat sur le parent (core + JDK).
 */
public class PluginClassLoader extends URLClassLoader {
    public PluginClassLoader(URL jar, ClassLoader parent) {
        super(new URL[] { jar }, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1) JDK/JNI natif : toujours parent-first (sinon bug)
        if (name.startsWith("java.") || name.startsWith("jdk.")) {
            return super.loadClass(name, resolve);
        }
        // 2) Essaie d'abord dans le JAR du plug-in
        try {
            Class<?> c = findClass(name);
            if (resolve) resolveClass(c);
            return c;
        } catch (ClassNotFoundException ignored) {
            // 3) Sinon parent
            return super.loadClass(name, resolve);
        }
    }
}
