package com.meekdev.moud.mod.place;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.meekdev.moud.core.place.PlaceConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class ServerLookup {

    public record Result(List<String> servers, String version, String download, String message, boolean open) {

        public boolean outdated(String running) {
            return !version.isEmpty() && compare(running, version) < 0;
        }
    }

    public record Found(Result result, @Nullable String problem) {}

    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration READ = Duration.ofSeconds(5);
    private static final int LIMIT = 64 * 1024;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ServerLookup() {}

    public static Result fetch(String url) throws IOException {
        return parse(download(url));
    }

    public static Found find(PlaceConfig.Host host, Path cache) throws IOException {
        if (host.lookup().isEmpty()) return new Found(new Result(List.of(host.address()), "", "", "", true), null);
        try {
            String text = download(host.lookup());
            Result result = parse(text);
            try {
                Files.createDirectories(cache.getParent());
                Files.writeString(cache, text);
            } catch (IOException ignored) {
            }
            return new Found(result, null);
        } catch (IOException fresh) {
            if (!Files.isRegularFile(cache)) throw fresh;
            try {
                return new Found(parse(Files.readString(cache)), fresh.getMessage());
            } catch (IOException stale) {
                throw fresh;
            }
        }
    }

    private static String download(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ)
                .header("Accept", "application/json, text/plain")
                .header("User-Agent", "Moud")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("stopped");
        } catch (HttpTimeoutException e) {
            throw new IOException(host(url) + " did not answer in time");
        } catch (IOException | IllegalArgumentException e) {
            throw new IOException("could not reach " + host(url));
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException(host(url) + " answered " + response.statusCode());
            byte[] bytes = body.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("the lookup file is over " + LIMIT / 1024 + " KB");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static Result parse(String text) throws IOException {
        JsonObject root;
        try {
            if (!(JsonParser.parseString(text) instanceof JsonObject object)) throw new IOException("the lookup file is not a JSON object");
            root = object;
        } catch (JsonParseException e) {
            throw new IOException("the lookup file is not valid JSON");
        }
        List<String> servers = new ArrayList<>();
        if (root.get("servers") instanceof JsonArray array) {
            for (JsonElement element : array) {
                String one = string(element);
                if (one == null) throw new IOException("servers is a list of addresses");
                if (!one.isBlank()) servers.add(one.strip());
            }
        } else if (root.has("servers")) {
            throw new IOException("servers is a list of addresses");
        }
        String address = string(root, "address");
        if (!address.isBlank()) servers.add(address.strip());
        boolean open = !(root.get("open") instanceof JsonPrimitive flag && flag.isBoolean() && !flag.getAsBoolean());
        if (open && servers.isEmpty()) throw new IOException("the lookup file lists no servers");
        String download = string(root, "download").strip();
        if (!download.isEmpty() && !download.toLowerCase(Locale.ROOT).startsWith("https://") && !download.toLowerCase(Locale.ROOT).startsWith("http://")) {
            download = "";
        }
        return new Result(List.copyOf(servers), string(root, "version").strip(), download, string(root, "message").strip(), open);
    }

    public static int compare(String a, String b) {
        int[] left = numbers(a);
        int[] right = numbers(b);
        for (int n = 0; n < Math.max(left.length, right.length); n++) {
            int x = n < left.length ? left[n] : 0;
            int y = n < right.length ? right[n] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] numbers(String version) {
        String core = version.strip().split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int n = 0; n < parts.length; n++) {
            String digits = parts[n].replaceAll("\\D.*", "");
            try {
                out[n] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                out[n] = Integer.MAX_VALUE;
            }
        }
        return out;
    }

    public static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static String string(JsonObject root, String key) throws IOException {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return "";
        String value = string(element);
        if (value == null) throw new IOException(key + " must be a string");
        return value;
    }

    private static @Nullable String string(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }
}
