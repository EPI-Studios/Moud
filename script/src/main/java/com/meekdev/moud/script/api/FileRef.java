package com.meekdev.moud.script.api;

// the place's own files, by res:// path
public interface FileRef {

    // null when there is no such file
    String read(String res);

    void write(String res, String text);
}
