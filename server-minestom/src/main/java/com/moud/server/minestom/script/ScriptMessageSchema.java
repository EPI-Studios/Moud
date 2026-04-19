package com.moud.server.minestom.script;

@FunctionalInterface
public interface ScriptMessageSchema {
    boolean validate(byte[] payload);

    ScriptMessageSchema ANY = payload -> true;

    ScriptMessageSchema NONE = payload -> false;
}
