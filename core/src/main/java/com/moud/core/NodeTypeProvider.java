package com.moud.core;

public interface NodeTypeProvider {
    default int order() {
        return 0;
    }

    void register(NodeTypeRegistry registry);
}
