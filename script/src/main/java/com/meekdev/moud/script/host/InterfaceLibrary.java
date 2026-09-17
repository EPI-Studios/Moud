package com.meekdev.moud.script.host;

import com.meekdev.moud.core.math.UDim2;

final class InterfaceLibrary {

    private InterfaceLibrary() {}

    static void install(Host host) {
        host.global("udim", "(scale: number, offset: number) -> UDim2", new Builtin("udim", a -> {
            double scale = a.number(0);
            double offset = a.number(1);
            return new UDim2(scale, offset, scale, offset);
        }));
    }
}
