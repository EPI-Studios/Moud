package com.moud.server.proxy;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.List;

public class JsArrayProxy implements ProxyArray {
    private final List<?> list;

    public JsArrayProxy(List<?> list) {
        this.list = list;
    }

    @Override
    public Object get(long index) {
        if (index < 0 || index >= list.size()) {
            throw new ArrayIndexOutOfBoundsException((int) index);
        }
        return list.get((int) index);
    }

    @Override
    public void set(long index, Value value) {
        throw new UnsupportedOperationException("Cannot modify this array from script.");
    }

    @Override
    public long getSize() {
        return list.size();
    }
}