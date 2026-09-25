package com.meekdev.moud.mod.client;

import java.util.List;
import java.util.Locale;

public final class ConnectReasons {

    private record Plain(List<String> raw, String text) {}

    private static final List<Plain> PLAIN = List.of(
            new Plain(List.of("connection refused"), "Connection refused"),
            new Plain(List.of("timed out", "timeout"), "Timed out"),
            new Plain(List.of("no route to host", "network is unreachable", "host is unreachable"), "The server is not answering"),
            new Plain(List.of("unknownhost", "unknown host", "unresolved", "name or service not known", "nodename nor servname"), "The server address was not found"),
            new Plain(List.of("server is full"), "The server is full"),
            new Plain(List.of("outdated", "incompatible client"), "The server runs another version"),
            new Plain(List.of("not whitelisted", "not white-listed"), "You are not on this server's whitelist"),
            new Plain(List.of("invalid session", "failed to verify username", "authentication servers"), "Could not sign in"),
            new Plain(List.of("connection reset", "connection lost", "broken pipe", "internal exception", "end of stream"), "The connection was lost"));

    private ConnectReasons() {}

    public static String friendly(String raw) {
        String text = raw.strip();
        if (text.isEmpty()) return "The connection closed";
        String lower = text.toLowerCase(Locale.ROOT);
        for (Plain plain : PLAIN) {
            for (String part : plain.raw()) {
                if (lower.contains(part)) return plain.text();
            }
        }
        return text;
    }
}
