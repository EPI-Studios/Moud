package com.meekdev.moud.script.api;

import java.util.List;
import java.util.function.UnaryOperator;

// saved data, as json text per key per named store. only a server has one
public interface StoreRef {

    // null when there is nothing under the key
    String get(String store, String key);

    // null removes the key
    void set(String store, String key, String json);

    // read, changed and written as one step that nothing else can interleave with. the change is handed
    // what is there, or null, and hands back what should be, or null to remove it
    String update(String store, String key, UnaryOperator<String> change);

    List<String> keys(String store, String prefix, int limit);

    // holds the key for owner for that many seconds, or renews it. false while somebody else holds it
    boolean lease(String store, String key, String owner, double seconds);

    void release(String store, String key, String owner);

    // who this server is when it holds a lease
    String owner();
}
