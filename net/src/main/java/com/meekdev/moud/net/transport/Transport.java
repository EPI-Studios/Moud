package com.meekdev.moud.net.transport;

import java.util.List;

// how a delivery gets from one side to the other
//
// the seam §6.1 names as an earned abstraction: it is a direct handoff when the server is in the same
// process as the client, and a packet channel when somebody else is hosting. the code above it never
// finds out which
//
// a remote is addressed by the id of the instance it is, not by a name. a name is a string two sides
// can disagree about silently; an id is the same thing the replication already agrees on, and one the
// far side either has or does not
public interface Transport {

    // from a client. it does not say who it is -- the far side knows, and a client that could say
    // would say whatever it liked
    void toServer(int remote, List<Object> args, boolean reliable);

    void toClient(String player, int remote, List<Object> args, boolean reliable);

    void toAllClients(int remote, List<Object> args, boolean reliable);

    // drained on the thread that owns the tree it fires into, never on the thread that sent
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
