package com.meekdev.moud.script.host;

@FunctionalInterface
public interface HostFunction {

    Object call(Args args);
}
