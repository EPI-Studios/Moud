package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.remote.RemoteFunction;
import com.meekdev.moud.core.remote.Schema;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.server.input.ServerClicks;
import com.meekdev.moud.mod.server.pilot.ServerPilot;
import com.meekdev.moud.mod.server.tool.ServerTools;
import com.meekdev.moud.mod.server.zone.ServerPrompts;
import com.meekdev.moud.mod.transport.payload.Payloads;
import com.meekdev.moud.net.transport.Wire;
import com.meekdev.moud.script.api.InvokeRef;
import com.meekdev.moud.script.api.PostRef;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Post {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/post");

    private static final Wired CARRIER = new Wired();

    private static Supplier<String> who = () -> "";

    public static final PostRef SERVER = ref(() -> "");
    public static final PostRef CLIENT = ref(() -> who.get());

    public static final InvokeRef CALLS = new InvokeRef() {
        @Override
        public void toServer(int remote, int call, List<Object> args) {
            CARRIER.callServer(Wired.INVOKE, remote, call, Wire.pack(args));
        }

        @Override
        public void toClient(String player, int remote, int call, List<Object> args) {
            CARRIER.callClient(player, Wired.INVOKE, remote, call, Wire.pack(args));
        }
    };

    private Post() {}

    public static Wired wired() {
        return CARRIER;
    }

    public static void install() {
        Payloads.register();
        CARRIER.listen();
        ServerChat.listen();
        ServerPrompts.listen();
        ServerClicks.listen();
        ServerPilot.listen();
        ServerTools.listen();
        Broadcast.listen();
    }

    public static void installOnClient(Supplier<String> me) {
        who = me;
        CARRIER.listenAsClient();
    }

    public static void drainToServer(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainServer((player, id, args) -> deliver(tree, id, player, args, true));
        CARRIER.drainCallsServer((player, kind, id, call, values) -> called(tree, player, kind, id, call, values, true));
    }

    public static void drainToClient(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainClient((id, args) -> deliver(tree, id, "", args, false));
        CARRIER.drainCallsClient((player, kind, id, call, values) -> called(tree, player, kind, id, call, values, false));
    }

    private static void deliver(InstanceTree tree, int id, String from, List<Object> args,
                                boolean toServer) {
        Instance instance = tree.byId(id);
        if (!(instance instanceof Remote remote)) return;
        List<Object> delivered = Wire.unpack(args, tree);
        try {
            Schema.check(remote, delivered);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("dropped a delivery on {}: {}", remote.name(), e.getMessage());
            return;
        }
        Remote.Sent sent = new Remote.Sent(from, delivered);
        try {
            if (toServer) {
                remote.onServer.fire(sent);
                remote.onServerPlayer.fire(sent);
            } else {
                remote.onClient.fire(sent);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("a handler on {} threw: {}", remote.name(), e.toString());
        }
    }

    private static void called(InstanceTree tree, String from, int kind, int id, int call, List<Object> raw, boolean toServer) {
        RemoteFunction.Reply reply = answer -> {
            int sent = answer.ok() ? Wired.ANSWERED : Wired.FAILED;
            List<Object> packed;
            try {
                packed = Wire.pack(answer.values());
            } catch (IllegalArgumentException e) {
                sent = Wired.FAILED;
                packed = List.of("the answer could not be sent: " + e.getMessage());
            }
            if (toServer) CARRIER.callClient(from, sent, id, call, packed);
            else CARRIER.callServer(sent, id, call, packed);
        };
        if (!(tree.byId(id) instanceof RemoteFunction remote)) {
            if (kind == Wired.INVOKE) reply.send(RemoteFunction.Answer.failed("that remote function no longer exists"));
            return;
        }
        List<Object> values = Wire.unpack(raw, tree);
        if (kind != Wired.INVOKE) {
            remote.answer(call, from, new RemoteFunction.Answer(kind == Wired.ANSWERED, values));
            return;
        }
        try {
            Schema.check(remote.name(), remote.accepts, values);
        } catch (IllegalArgumentException e) {
            reply.send(RemoteFunction.Answer.failed(e.getMessage()));
            return;
        }
        Callback handler = toServer ? remote.onServerInvoke : remote.onClientInvoke;
        if (!handler.isSet()) {
            reply.send(RemoteFunction.Answer.failed(remote.name() + " has no " + (toServer ? "onServerInvoke" : "onClientInvoke")));
            return;
        }
        try {
            handler.call(reply, from, values);
        } catch (RuntimeException e) {
            LOGGER.warn("a handler on {} threw: {}", remote.name(), e.toString());
            reply.send(RemoteFunction.Answer.failed(remote.name() + " failed"));
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
