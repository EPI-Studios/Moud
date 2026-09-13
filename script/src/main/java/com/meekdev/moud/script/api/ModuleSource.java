package com.meekdev.moud.script.api;

// where require reads a module from. path is already checked and relative to the place
public interface ModuleSource {

    // null when there is no such file. throws when the file exists but this side may not have it
    String read(String path);
}
