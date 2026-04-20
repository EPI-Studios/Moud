package com.moud.client.fabric.uri;

import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.uri.MoudUriIpc;
import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientUriServer implements AutoCloseable {
    private static final String TAG = "Link";

    private final Queue<String> pendingUris = new ConcurrentLinkedQueue<>();
    private volatile ServerSocket socket;
    private volatile Thread acceptThread;

    public synchronized boolean start() {
        if (socket != null && !socket.isClosed()) {
            return true;
        }
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName(MoudUriIpc.LOOPBACK_HOST), MoudUriIpc.LOOPBACK_PORT));
            socket = server;
            acceptThread = Thread.ofPlatform()
                    .daemon(true)
                    .name("moud-uri-ipc")
                    .start(this::acceptLoop);
            ClientDebugLog.info(TAG, "Listening for moud:// links on " + MoudUriIpc.LOOPBACK_HOST + ":" + MoudUriIpc.LOOPBACK_PORT);
            return true;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "link listener unavailable: " + e.getMessage());
            socket = null;
            acceptThread = null;
            return false;
        }
    }

    public List<String> drain() {
        ArrayList<String> drained = new ArrayList<>();
        String next;
        while ((next = pendingUris.poll()) != null) {
            drained.add(next);
        }
        return List.copyOf(drained);
    }

    private void acceptLoop() {
        while (true) {
            ServerSocket server = socket;
            if (server == null || server.isClosed()) {
                return;
            }
            try (Socket client = server.accept()) {
                client.setSoTimeout(Math.toIntExact(MoudUriIpc.IO_TIMEOUT.toMillis()));
                try (DataInputStream in = new DataInputStream(client.getInputStream())) {
                    String rawUri = in.readUTF();
                    if (rawUri != null && !rawUri.isBlank() && rawUri.length() <= MoudUriIpc.MAX_URI_CHARS) {
                        pendingUris.add(rawUri);
                    }
                }
            } catch (Exception e) {
                ServerSocket current = socket;
                if (current == null || current.isClosed()) {
                    return;
                }
                ClientDebugLog.warn(TAG, "link receive failed: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        Thread thread = acceptThread;
        acceptThread = null;
        ServerSocket server = socket;
        socket = null;
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
        if (thread != null) {
            thread.interrupt();
        }
        pendingUris.clear();
    }
}
