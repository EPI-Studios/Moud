package com.meekdev.moud.mod.client.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.moud.mod.MoudMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jspecify.annotations.Nullable;

public final class ForumLink {

    public static final String DEFAULT_API = "https://api.moud.epistudios.fr";
    public static final String DEFAULT_SITE = "moud.epistudios.fr/forum/link";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String PROPERTY = "moud.api";
    private static final String ENVIRONMENT = "MOUD_API";
    private static final String SITE_PROPERTY = "moud.site";
    private static final String SITE_ENVIRONMENT = "MOUD_SITE";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ForumLink() {}

    public record Code(String code, int expiresIn, String name) {}

    public static String api() {
        return chosen(PROPERTY, ENVIRONMENT, DEFAULT_API);
    }

    public static String site() {
        return chosen(SITE_PROPERTY, SITE_ENVIRONMENT, DEFAULT_SITE);
    }

    private static String chosen(String property, String environment, String fallback) {
        String given = System.getProperty(property, System.getenv(environment));
        if (given == null || given.isBlank()) return fallback;
        return given.endsWith("/") ? given.substring(0, given.length() - 1) : given;
    }

    public static CompletableFuture<Code> start() {
        User user = Minecraft.getInstance().getUser();
        if (user == null) return failed("sign in to Minecraft first");

        String username = user.getName();
        if (username == null || username.isBlank()) return failed("this launcher gave no account name");

        return challenge()
                .thenApply(serverId -> {
                    proveToMojang(user, serverId);
                    return serverId;
                })
                .thenCompose(serverId -> claim(username, serverId));
    }

    private static CompletableFuture<String> challenge() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api() + "/v1/challenge"))
                .timeout(TIMEOUT)
                .header("accept", "application/json")
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(answer -> {
                    JsonObject body = read(answer);
                    if (answer.statusCode() != 200) throw new IllegalStateException(why(body, "the api refused to start"));
                    String serverId = text(body, "serverId");
                    if (serverId == null) throw new IllegalStateException("the api sent no challenge");
                    return serverId;
                });
    }

    private static void proveToMojang(User user, String serverId) {
        try {
            Minecraft.getInstance().services().sessionService()
                    .joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (Exception problem) {
            MoudMod.LOG.warn("could not prove the Minecraft account", problem);
            throw new IllegalStateException("Mojang would not confirm this account: " + problem.getMessage());
        }
    }

    private static CompletableFuture<Code> claim(String username, String serverId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("serverId", serverId);
        payload.addProperty("username", username);

        HttpRequest request = HttpRequest.newBuilder(URI.create(api() + "/v1/claim"))
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(answer -> {
                    JsonObject body = read(answer);
                    if (answer.statusCode() != 200) throw new IllegalStateException(why(body, "the api refused the login"));
                    String code = text(body, "code");
                    if (code == null) throw new IllegalStateException("the api sent no code");
                    int expires = body.has("expiresIn") ? body.get("expiresIn").getAsInt() : 600;
                    String name = text(body, "name");
                    return new Code(code, expires, name == null ? username : name);
                });
    }

    private static JsonObject read(HttpResponse<String> answer) {
        try {
            return JsonParser.parseString(answer.body()).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static @Nullable String text(JsonObject body, String name) {
        return body.has(name) && body.get(name).isJsonPrimitive() ? body.get(name).getAsString() : null;
    }

    private static String why(JsonObject body, String fallback) {
        String given = text(body, "error");
        return given == null ? fallback : given;
    }

    private static <T> CompletableFuture<T> failed(String why) {
        return CompletableFuture.failedFuture(new IllegalStateException(why));
    }
}
