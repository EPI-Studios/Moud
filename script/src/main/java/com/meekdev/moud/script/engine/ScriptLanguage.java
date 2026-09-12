package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import java.io.IOException;
import java.nio.file.Path;

// a language a place may be written in
//
// the extension is how one is chosen: a place is a folder with a main file in it, and which
// language runs it is the answer to what that file is called. there is no manifest and nothing to
// declare -- naming the file is the declaration
public interface ScriptLanguage {

    String name();

    // without the dot, so "luau" and not ".luau"
    String extension();

    ScriptEngine engine();

    // the definitions an editor reads a place against, if the language has any. rewritten on
    // every start so they always describe the engine that is about to run it
    default void writeTypes(Path place, ClassRegistry classes) throws IOException {}
}
