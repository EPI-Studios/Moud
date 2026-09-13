package com.meekdev.moud.script.api;

public interface FileRef {

    String read(String res);

    void write(String res, String text);
}
