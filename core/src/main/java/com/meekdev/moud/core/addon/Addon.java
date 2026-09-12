package com.meekdev.moud.core.addon;

import com.meekdev.moud.core.clazz.ClassRegistry;

// something that extends the engine from outside it
//
// the class set is built rather than fixed: an addon adds its own classes to it and everything
// downstream follows without being told. a class carries properties, so a registered one is
// replicated, scriptable and declared in the editor's types for free -- none of those three had
// to learn about it
//
// 19.1 allows a plug in point exactly here and nowhere by default: "no service loaders unless a
// real third party needs to plug in". this is that door, and it is the only one
public interface Addon {

    // what it is called in an error, so a class registered twice names who registered it
    String id();

    // called once per side, before anything reads the registry. an addon that adds nothing here
    // is still an addon: this is the first hook, not the only one
    default void classes(ClassRegistry registry) {}
}
