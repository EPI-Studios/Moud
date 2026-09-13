package com.meekdev.moud.core.addon;

import com.meekdev.moud.core.clazz.ClassRegistry;

public interface Addon {

    String id();

    default void classes(ClassRegistry registry) {}
}
