package com.meekdev.moud.net.transport;

import java.util.List;

public interface Transport {

    void toServer(int remote, List<Object> args, boolean reliable);

    void toClient(String player, int remote, List<Object> args, boolean reliable);

    void toAllClients(int remote, List<Object> args, boolean reliable);

    void drainServer(ServerSink sink);

    void drainClient(ClientSink sink);

    @FunctionalInterface
    interface ServerSink {
        void deliver(String player, int remote, List<Object> args);
    }

    @FunctionalInterface
    interface ClientSink {
        void deliver(int remote, List<Object> args);
    }
}
