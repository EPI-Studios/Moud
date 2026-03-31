package com.moud.server.minestom.scripting;

interface ScriptObject extends AutoCloseable {
    boolean hasMethod(String member);

    void invokeMethod(String member, Object... args) throws ScriptInvocationException;

    @Override
    default void close() throws Exception {
    }
}
