package com.meekdev.moud.mod.adapter.http;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.script.api.HttpRef;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpRequests implements HttpRef {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int LARGEST = 16 * 1024 * 1024;

    public static final HttpRequests INSTANCE = new HttpRequests();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpRequests() {}

    @Override
    public CompletableFuture<Response> request(String method, String url, Map<String, String> headers, String body) {
        if (!MoudMod.features().isOn(Feature.HTTP_REQUESTS)) {
            throw new IllegalStateException("http requests are off, turn on httpRequests in place.toml [features]");
        }
        if (body.length() > LARGEST) throw new IllegalArgumentException("the request body is larger than 16 MB");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
            String text = response.body();
            if (text != null && text.length() > LARGEST) text = text.substring(0, LARGEST);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> header : response.headers().map().entrySet()) out.put(header.getKey(), String.join(", ", header.getValue()));
            return new Response(response.statusCode(), text == null ? "" : text, out);
        });
    }
}
