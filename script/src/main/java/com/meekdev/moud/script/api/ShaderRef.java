package com.meekdev.moud.script.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ShaderRef {

    Patch patch(List<String> targets, Map<String, Object> spec, Consumer<String> problems);

    interface Patch {
        void set(String uniform, Object value);

        void remove();
    }
}
