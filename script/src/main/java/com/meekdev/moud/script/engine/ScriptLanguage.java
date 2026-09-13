package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import java.io.IOException;
import java.nio.file.Path;

public interface ScriptLanguage {

    String name();

    String extension();

    ScriptEngine engine();

    default void writeTypes(Path place, ClassRegistry classes) throws IOException {}
}
