package com.meekdev.moud.script.host.data;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.script.api.HttpRef;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Suspend;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class HttpLibrary {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private HttpLibrary() {}

    public static void install(Host host) {
        Members http = new Members("Http")
                .function("jsonEncode", "(value: any) -> string", a -> StoreLibrary.encode(a.get(0)))
                .function("jsonDecode", "(text: string) -> any", a -> {
                    try {
                        return Json.parse(a.string(0));
                    } catch (RuntimeException e) {
                        throw new HostError("jsonDecode could not read that text: %s", e.getMessage());
                    }
                })
                .function("generateGuid", "(wrapInBraces: boolean?) -> string", a -> {
                    String guid = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
                    return Boolean.FALSE.equals(a.get(0)) ? guid : "{" + guid + "}";
                })
                .function("urlEncode", "(text: string) -> string", a -> URLEncoder.encode(a.string(0), StandardCharsets.UTF_8).replace("+", "%20"));
        HttpRef ref = host.http();
        if (ref != null) {
            host.api().declare(new Members("HttpResponse")
                    .declare("status", "number")
                    .declare("ok", "boolean")
                    .declare("body", "string")
                    .declare("headers", "{ [string]: string }").decl());
            http.function("get", "(url: string, headers: { [string]: string }?) -> HttpResponse", a -> send(ref, "GET", a.string(0), headers(a.map(1, Map.of())), ""))
                    .function("post", "(url: string, body: string, contentType: string?, headers: { [string]: string }?) -> HttpResponse", a -> {
                        Map<String, String> headers = headers(a.map(3, Map.of()));
                        headers.putIfAbsent("Content-Type", a.has(2) ? a.string(2) : "application/json");
                        return send(ref, "POST", a.string(0), headers, a.string(1));
                    })
                    .function("request", "(options: { url: string, method: string?, headers: { [string]: string }?, body: string? }) -> HttpResponse", a -> {
                        Map<String, Object> options = a.map(0, Map.of());
                        if (!(options.get("url") instanceof String url)) throw new HostError("http.request needs a url");
                        String method = options.get("method") instanceof String m ? m.toUpperCase(Locale.ROOT) : "GET";
                        if (!METHODS.contains(method)) throw new HostError("'%s' is not an http method", method);
                        Object headers = options.get("headers");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> given = headers instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
                        return send(ref, method, url, headers(given), options.get("body") instanceof String body ? body : "");
                    });
        }
        host.global("http", "Http", http);
        host.declare(http);
    }

    private static Map<String, String> headers(Map<String, Object> given) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : given.entrySet()) {
            if (!(entry.getValue() instanceof String value)) throw new HostError("header %s must be text", entry.getKey());
            headers.put(entry.getKey(), value);
        }
        return headers;
    }

    private static Suspend send(HttpRef ref, String method, String url, Map<String, String> headers, String body) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw new HostError("http only reaches http:// and https:// addresses, got %s", url);
        CompletableFuture<HttpRef.Response> future;
        try {
            future = ref.request(method, url, headers, body);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new HostError("%s", e.getMessage());
        }
        return new Suspend(dt -> {
            if (!future.isDone()) return null;
            try {
                HttpRef.Response response = future.join();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("status", (double) response.status());
                out.put("ok", response.status() >= 200 && response.status() < 300);
                out.put("body", response.body());
                out.put("headers", new LinkedHashMap<String, Object>(response.headers()));
                return new Object[] {out};
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                return Suspend.failed("%s %s failed: %s", method, url, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
        }, () -> future.cancel(true));
    }
}
