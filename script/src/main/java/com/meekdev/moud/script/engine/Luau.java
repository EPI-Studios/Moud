package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.types.Types;
import com.meekdev.moud.script.vm.Vm;
import java.io.IOException;
import java.nio.file.Path;

// the one the engine ships with
public final class Luau implements ScriptLanguage {

    @Override
    public String name() {
        return "luau";
    }

    @Override
    public String extension() {
        return "luau";
    }

    @Override
    public ScriptEngine engine() {
        return new Vm();
    }

    @Override
    public void writeTypes(Path place, ClassRegistry classes) throws IOException {
        Types.write(place, classes);
    }
}
