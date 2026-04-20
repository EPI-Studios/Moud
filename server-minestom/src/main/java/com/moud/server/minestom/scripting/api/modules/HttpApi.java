package com.moud.server.minestom.scripting.api.modules;

import com.moud.server.minestom.scripting.http.HttpScheduler;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public final class HttpApi {

    public HttpApi() { }

    @HostAccess.Export
    public void get(String url, Value callback) {
        get(url, null, callback);
    }

    @HostAccess.Export
    public void get(String url, Value headers, Value callback) {
        if (callback == null || !callback.canExecute()) return;
        HttpScheduler.get(url, toStringMap(headers), wrap(callback));
    }

    @HostAccess.Export
    public void postJson(String url, String body, Value callback) {
        postJson(url, body, null, callback);
    }

    @HostAccess.Export
    public void postJson(String url, String body, Value headers, Value callback) {
        if (callback == null || !callback.canExecute()) return;
        HttpScheduler.postJson(url, body, toStringMap(headers), wrap(callback));
    }

    @HostAccess.Export
    public void postForm(String url, Value form, Value callback) {
        postForm(url, form, null, callback);
    }

    @HostAccess.Export
    public void postForm(String url, Value form, Value headers, Value callback) {
        if (callback == null || !callback.canExecute()) return;
        HttpScheduler.postForm(url, toStringMap(form), toStringMap(headers), wrap(callback));
    }

    private static HttpScheduler.Callback wrap(Value cb) {
        return (status, body, headers, err) -> {
            try {
                cb.executeVoid(status, body == null ? "" : body, headers, err == null ? "" : err);
            } catch (Throwable ignored) { }
        };
    }

    private static Map<String, String> toStringMap(Value v) {
        if (v == null || v.isNull()) return null;
        if (!v.hasMembers()) return null;
        HashMap<String, String> out = new HashMap<>();
        for (String key : v.getMemberKeys()) {
            Value mv = v.getMember(key);
            if (mv == null || mv.isNull()) continue;
            out.put(key, mv.toString());
        }
        return out;
    }
}
