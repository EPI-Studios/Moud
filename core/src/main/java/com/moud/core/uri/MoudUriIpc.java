package com.moud.core.uri;

import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;

public final class MoudUriIpc {
    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int LOOPBACK_PORT = 37721;
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(750);
    public static final Duration IO_TIMEOUT = Duration.ofMillis(1500);
    public static final int MAX_URI_CHARS = 4096;

    private MoudUriIpc() {
    }

    public static boolean sendToRunningClient(String rawUri) {
        Objects.requireNonNull(rawUri, "rawUri");
        String payload = rawUri.trim();
        if (payload.isEmpty() || payload.length() > MAX_URI_CHARS) {
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), LOOPBACK_PORT),
                    Math.toIntExact(CONNECT_TIMEOUT.toMillis()));
            socket.setSoTimeout(Math.toIntExact(IO_TIMEOUT.toMillis()));
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                out.writeUTF(payload);
                out.flush();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
