package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public interface ScriptLanguage {

    String name();

    List<String> extensions();

    ScriptEngine start(Host host);

    default void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {}

    default void writeScene(Path place, Instance world, Predicate<Instance> keep) throws IOException {}
}
