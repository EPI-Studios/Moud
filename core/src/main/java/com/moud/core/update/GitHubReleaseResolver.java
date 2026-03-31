package com.moud.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class GitHubReleaseResolver {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String owner;
    private final String repo;
    private final String publicKeyPem;
    private final HttpClient http;

    public GitHubReleaseResolver(String owner, String repo, String publicKeyPem) {
        this(owner, repo, publicKeyPem, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public GitHubReleaseResolver(String owner, String repo, String publicKeyPem, HttpClient http) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner is required");
        if (repo == null || repo.isBlank()) throw new IllegalArgumentException("repo is required");
        if (publicKeyPem == null || publicKeyPem.isBlank()) throw new IllegalArgumentException("publicKeyPem is required");
        this.owner = owner.trim();
        this.repo = repo.trim();
        this.publicKeyPem = publicKeyPem;
        this.http = http;
    }

    public ReleaseManifest fetchLatest(boolean includePrerelease) throws IOException, InterruptedException {
        JsonObject release = fetchLatestRelease(includePrerelease);
        if (release == null) return null;
        return resolveFromRelease(release);
    }
    public ReleaseManifest fetchByTag(String tag) throws IOException, InterruptedException {
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("tag is required");
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/tags/" + tag.trim();
        String body = httpGet(url);
        if (body == null) return null;
        JsonObject release = JsonParser.parseString(body).getAsJsonObject();
        return resolveFromRelease(release);
    }

    private ReleaseManifest resolveFromRelease(JsonObject release) throws IOException, InterruptedException {
        String manifestUrl = findAssetUrl(release, "release.json");
        String sigUrl = findAssetUrl(release, "release.sig");
        if (manifestUrl == null || sigUrl == null) return null;

        String manifestJson = httpGet(manifestUrl);
        String signatureBase64 = httpGet(sigUrl);
        if (manifestJson == null || signatureBase64 == null) return null;

        return ReleaseManifestVerifier.verifyAndParse(
                manifestJson, signatureBase64.trim(), publicKeyPem);
    }

    private JsonObject fetchLatestRelease(boolean includePrerelease) throws IOException, InterruptedException {
        if (!includePrerelease) {
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            String body = httpGet(url);
            if (body != null) {
                return JsonParser.parseString(body).getAsJsonObject();
            }
            return null;
        }

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=10";
        String body = httpGet(url);
        if (body == null) return null;

        JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
        for (JsonElement element : releases) {
            JsonObject rel = element.getAsJsonObject();
            if (rel.has("draft") && rel.get("draft").getAsBoolean()) continue;
            return rel;
        }
        return null;
    }

    private static String findAssetUrl(JsonObject release, String assetName) {
        if (release == null || !release.has("assets")) return null;
        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (assetName.equals(name)) {
                return asset.has("browser_download_url")
                        ? asset.get("browser_download_url").getAsString()
                        : null;
            }
        }
        return null;
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/octet-stream, application/json")
                .header("User-Agent", "moud-updater/1.0")
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }
}
