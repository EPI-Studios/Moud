package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.Schema;
import com.meekdev.moud.net.transport.Transport;
import com.meekdev.moud.net.transport.Wire;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.server.ServerPrompts;
import com.meekdev.moud.mod.server.ServerPilot;
import com.meekdev.moud.script.api.PostRef;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.meekdev.moud.mod.transport.payload.Payloads;

public final class Post {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/post");

    private static final Wired CARRIER = new Wired();

    private static Supplier<String> who = () -> "";

    public static final PostRef SERVER = ref(() -> "");
    public static final PostRef CLIENT = ref(() -> who.get());

    private Post() {}

    public static Wired wired() {
        return CARRIER;
    }

    public static void install() {
        Payloads.register();
        CARRIER.listen();
        ServerChat.listen();
        ServerPrompts.listen();
        ServerPilot.listen();
        Broadcast.listen();
    }

    public static void installOnClient(Supplier<String> me) {
        who = me;
        CARRIER.listenAsClient();
    }

    public static void drainToServer(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainServer((player, id, args) -> deliver(tree, id, player, args, true));
    }

    public static void drainToClient(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainClient((id, args) -> deliver(tree, id, "", args, false));
    }

    private static void deliver(InstanceTree tree, int id, String from, List<Object> args,
                                boolean toServer) {
        Instance instance = tree.byId(id);
        if (!(instance instanceof Remote remote)) return;
        List<Object> delivered = Wire.unpack(args, tree);
        try {
            Schema.check(remote, delivered);
        } catch (IllegalArgumentException wrong) {
            LOGGER.warn("dropped a delivery on {}: {}", remote.name(), wrong.getMessage());
            return;
        }
        Remote.Sent sent = new Remote.Sent(from, delivered);
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

    private static PostRef ref(Supplier<String> who) {
        return new PostRef() {
            @Override
            public String me() {
                return who.get();
            }

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
