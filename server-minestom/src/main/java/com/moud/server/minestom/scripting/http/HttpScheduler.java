package com.moud.server.minestom.scripting.http;

import com.moud.server.minestom.util.DebugLog;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpScheduler {

    private static final String LOG_TAG = "script-http";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final ConcurrentLinkedQueue<Runnable> CALLBACKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "moud-http-" + THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(EXECUTOR)
            .build();

    private HttpScheduler() { }

    public interface Callback {
        void done(int status, String body, Map<String, String> headers, String err);
    }

    public static void get(String url, Map<String, String> headers, Callback cb) {
        send("GET", url, null, null, headers, cb);
    }

    public static void postJson(String url, String jsonBody, Map<String, String> headers, Callback cb) {
        Map<String, String> h = headers == null ? new HashMap<>() : new HashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/json");
        send("POST", url, jsonBody, null, h, cb);
    }

    public static void postForm(String url, Map<String, String> form, Map<String, String> headers, Callback cb) {
        String body = encodeForm(form);
        Map<String, String> h = headers == null ? new HashMap<>() : new HashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
        send("POST", url, body, null, h, cb);
    }

    private static void send(String method, String url, String textBody, byte[] binBody,
                             Map<String, String> headers, Callback cb) {
        if (cb == null) return;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            enqueue(() -> cb.done(0, "", Map.of(), "invalid url"));
            return;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            enqueue(() -> cb.done(0, "", Map.of(), "unsupported scheme: " + scheme));
            return;
        }
        String host = uri.getHost();
        if (host == null || isPrivateHost(host)) {
            enqueue(() -> cb.done(0, "", Map.of(), "host blocked: " + host));
            return;
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(DEFAULT_TIMEOUT);
        HttpRequest.BodyPublisher body;
        if (binBody != null) body = HttpRequest.BodyPublishers.ofByteArray(binBody);
        else if (textBody != null) body = HttpRequest.BodyPublishers.ofString(textBody);
        else body = HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case "GET" -> rb.GET();
            case "POST" -> rb.POST(body);
            case "PUT" -> rb.PUT(body);
            case "DELETE" -> rb.DELETE();
            default -> rb.method(method, body);
        }

        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                try {
                    rb.header(e.getKey(), e.getValue());
                } catch (IllegalArgumentException ignored) { }
            }
        }

        CLIENT.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        String msg = err.getMessage();
                        enqueue(() -> cb.done(0, "", Map.of(), msg == null ? err.getClass().getSimpleName() : msg));
                        return;
                    }
                    String text = resp.body();
                    if (text != null && text.length() > MAX_RESPONSE_BYTES) {
                        text = text.substring(0, MAX_RESPONSE_BYTES);
                    }
                    HashMap<String, String> respHeaders = new HashMap<>();
                    resp.headers().map().forEach((k, vs) -> {
                        if (!vs.isEmpty()) respHeaders.put(k, vs.get(0));
                    });
                    final String finalText = text == null ? "" : text;
                    final int status = resp.statusCode();
                    enqueue(() -> cb.done(status, finalText, respHeaders, null));
                });
    }

    public static void pump() {
        Runnable r;
        while ((r = CALLBACKS.poll()) != null) {
            try {
                r.run();
            } catch (Throwable t) {
                DebugLog.warn(LOG_TAG, "http callback error: " + t.getMessage());
            }
        }
    }

    private static void enqueue(Runnable r) {
        CALLBACKS.add(r);
    }

    private static boolean isPrivateHost(String host) {
        if (host == null) return true;
        String lower = host.toLowerCase();
        if (lower.equals("localhost")) return true;
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                return true;
            }
            byte[] b = addr.getAddress();
            if (b.length == 4) {
                int a = b[0] & 0xff;
                if (a == 10 || a == 127) return true;
                if (a == 169 && (b[1] & 0xff) == 254) return true;
                if (a == 172 && ((b[1] & 0xff) >= 16 && (b[1] & 0xff) <= 31)) return true;
                if (a == 192 && (b[1] & 0xff) == 168) return true;
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    private static String encodeForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
