package com.meekdev.moud.script.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface HttpRef {

    record Response(int status, String body, Map<String, String> headers) {}

    CompletableFuture<Response> request(String method, String url, Map<String, String> headers, String body);
}
