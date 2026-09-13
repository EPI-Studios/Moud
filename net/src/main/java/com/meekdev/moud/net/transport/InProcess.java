package com.meekdev.moud.net.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class InProcess implements Transport {

    private static final int RELIABLE_DEPTH = 4096;
    private static final int UNRELIABLE_DEPTH = 256;

    private record Up(String player, int remote, List<Object> args) {}

    private record Down(String player, int remote, List<Object> args) {}

    private final Queue<Up> up = new ArrayDeque<>();
    private final Queue<Down> down = new ArrayDeque<>();

    private volatile String me = "";

    private final Map<Integer, Integer> sent = new HashMap<>();

    private int droppedUp;

    public void identify(String player) {
        this.me = player;
    }

    public static final int PER_TICK = 6;

    @Override
    public void toServer(int remote, List<Object> args, boolean reliable) {
        synchronized (up) {
            int already = sent.merge(remote, 1, Integer::sum);
            if (already > PER_TICK) {
                droppedUp++;
                return;
            }
            if (up.size() >= (reliable ? RELIABLE_DEPTH : UNRELIABLE_DEPTH)) {
                if (reliable) {
                    droppedUp++;
                    return;
                }
                up.poll();
            }
            up.add(new Up(me, remote, args));
        }
    }

    @Override
    public void toClient(String player, int remote, List<Object> args, boolean reliable) {
        synchronized (down) {
            if (down.size() >= (reliable ? RELIABLE_DEPTH : UNRELIABLE_DEPTH)) {
                if (reliable) return;
                down.poll();
            }
            down.add(new Down(player, remote, args));
        }
    }

    @Override
    public void toAllClients(int remote, List<Object> args, boolean reliable) {
        toClient("", remote, args, reliable);
    }

    @Override
    public void drainServer(ServerSink sink) {
        List<Up> batch;
        synchronized (up) {
            if (up.isEmpty()) {
                sent.clear();
                return;
            }
            batch = new ArrayList<>(up);
            up.clear();
            sent.clear();
        }
        for (Up one : batch) sink.deliver(one.player(), one.remote(), one.args());
    }

    @Override
    public void drainClient(ClientSink sink) {
        List<Down> batch;
        synchronized (down) {
            if (down.isEmpty()) return;
            batch = new ArrayList<>(down);
            down.clear();
        }
        for (Down one : batch) {
            if (one.player().isEmpty() || one.player().equals(me)) {
                sink.deliver(one.remote(), one.args());
            }
        }
    }

    public int droppedFromClients() {
        return droppedUp;
    }
}
