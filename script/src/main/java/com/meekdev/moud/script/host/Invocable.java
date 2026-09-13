package com.meekdev.moud.script.host;

public interface Invocable extends HostObject {

    Object invoke(Args args);
}
