package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ScriptLanguage {

    String name();

    List<String> extensions();

    ScriptEngine start(Host host);

    default void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {}
}
