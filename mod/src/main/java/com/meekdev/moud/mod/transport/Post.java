package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.net.transport.InProcess;
import com.meekdev.moud.net.transport.Transport;
import com.meekdev.moud.net.transport.Wire;
import com.meekdev.moud.script.api.PostRef;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// the one carrier both sides hold, and the two drains that turn a delivery into a signal
//
// one instance because the server this client talks to is in the same process today. the seam is what
// matters: when somebody else is hosting, this holds a packet channel instead and nothing above it
// finds out
public final class Post {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/post");

    private static final InProcess CARRIER = new InProcess();

    // a place's side of it, per side, because the guard on which verb is allowed is per side
    public static final PostRef SERVER = ref();
    public static final PostRef CLIENT = ref();

    private Post() {}

    public static InProcess carrier() {
        return CARRIER;
    }

    // who this client is. the server never reads it, and a client that could set it to somebody else
    // would only be lying to itself: the far side stamps every delivery with whoever it came from
    public static void identify(String player) {
        CARRIER.identify(player);
    }

    // what clients sent, on the server's tick
    public static void drainToServer(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainServer((player, id, args) -> deliver(tree, id, player, args, true));
    }

    // what the server sent, on the client's tick
    public static void drainToClient(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainClient((id, args) -> deliver(tree, id, "", args, false));
    }

    private static void deliver(InstanceTree tree, int id, String from, List<Object> args,
                                boolean toServer) {
        Instance instance = tree.byId(id);
        // a channel that has been destroyed since the delivery left is not an error: it is the
        // ordinary race between one side closing it and the other hearing about it
        if (!(instance instanceof Remote remote)) return;
        Remote.Sent sent = new Remote.Sent(from, Wire.unpack(args, tree));
        // a handler that throws must not take the rest of the batch with it, nor the tick
        try {
            if (toServer) {
                remote.onServer.fire(sent);
            } else {
                remote.onClient.fire(sent);
            }
        } catch (RuntimeException failed) {
            LOGGER.warn("a handler on {} threw: {}", remote.name(), failed.toString());
        }
    }

    private static PostRef ref() {
        return new PostRef() {
            @Override
            public void toServer(int remote, List<Object> args, boolean reliable) {
                CARRIER.toServer(remote, Wire.pack(args), reliable);
            }

            @Override
            public void toClient(String player, int remote, List<Object> args, boolean reliable) {
                CARRIER.toClient(player, remote, Wire.pack(args), reliable);
            }

            @Override
            public void toAllClients(int remote, List<Object> args, boolean reliable) {
                CARRIER.toAllClients(remote, Wire.pack(args), reliable);
            }
        };
    }
}
