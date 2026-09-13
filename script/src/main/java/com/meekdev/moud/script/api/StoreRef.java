package com.meekdev.moud.script.api;

import java.util.List;
import java.util.function.UnaryOperator;

public interface StoreRef {

    String get(String store, String key);

    void set(String store, String key, String json);

    String update(String store, String key, UnaryOperator<String> change);

    List<String> keys(String store, String prefix, int limit);

    boolean lease(String store, String key, String owner, double seconds);

    void release(String store, String key, String owner);

    String owner();
}
