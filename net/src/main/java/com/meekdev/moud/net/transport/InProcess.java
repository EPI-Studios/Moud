package com.meekdev.moud.net.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// both sides in one process, which is what a solo game is
//
// the queues are the whole of it. they exist rather than a direct call because the two sides run on
// different threads and on different clocks: a client firing during its frame must not land in the
// middle of a server tick, so it waits in a queue the server drains where it drains everything else
//
// unreliable is honoured rather than ignored. in one process nothing is ever lost, so what it means
// here is a queue that drops the oldest when it is full instead of growing -- which is the behaviour
// that matters, because the caller has to already be correct under loss
public final class InProcess implements Transport {

    // a tick's worth of input at a generous rate, per direction. past this something is wrong and
    // growing the queue only delays finding out
    private static final int RELIABLE_DEPTH = 4096;
    private static final int UNRELIABLE_DEPTH = 256;

    private record Up(String player, int remote, List<Object> args) {}

    private record Down(String player, int remote, List<Object> args) {}

    private final Queue<Up> up = new ArrayDeque<>();
    private final Queue<Down> down = new ArrayDeque<>();

    // who this client is, on the side that is a client. the server never reads it
    private volatile String me = "";

    // how many a client has sent down each remote since the last drain, which is the only rate any
    // of this needs: the server drains once a tick, so a per drain count *is* a per tick rate
    private final Map<Integer, Integer> sent = new HashMap<>();

    private int droppedUp;

    public void identify(String player) {
        this.me = player;
    }

    // six a tick down one channel is far more than a keypress needs and far less than a script in a
    // loop can flood with. over it, the rest of the tick is dropped and said so once
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
        // one process, one client. everybody is the only one there is
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
            // addressed at everybody, or at this one
            if (one.player().isEmpty() || one.player().equals(me)) {
                sink.deliver(one.remote(), one.args());
            }
        }
    }

    public int droppedFromClients() {
        return droppedUp;
    }
}
