package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.remote.RemoteFunction;
import com.meekdev.moud.core.remote.Schema;
import com.meekdev.moud.script.api.InvokeRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Suspend;
import com.meekdev.moud.script.host.player.Players;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

public final class RemoteFunctions {

    private RemoteFunctions() {}

    public static void install(Host host) {
        Members functions = host.instances().of(Classes.REMOTE_FUNCTION);
        functions.method("invokeServer", "(...any) -> ...any", a -> {
            RemoteFunction remote = remote(a);
            if (!host.client()) throw new HostError("invokeServer is client-only");
            List<Object> args = declared(remote, a, 1);
            return call(remote, "", "the server", call -> invoke(host).toServer(remote.id(), call, args));
        });
        functions.method("invokeClient", "(player: Player, ...any) -> ...any", a -> {
            RemoteFunction remote = remote(a);
            if (host.client()) throw new HostError("invokeClient is server-only");
            String player = Players.idOf(a.get(1));
            if (player == null) throw new HostError("invokeClient expects a player as the first argument");
            List<Object> args = declared(remote, a, 2);
            return call(remote, player, "the client", call -> invoke(host).toClient(player, remote.id(), call, args));
        });
    }

    public static void serve(Host host, RemoteFunction remote, boolean onServer, Callable fn, Object owner, String where, Object[] delivered) {
        RemoteFunction.Reply reply = (RemoteFunction.Reply) delivered[0];
        String from = (String) delivered[1];
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) delivered[2];
        List<Object> args = new ArrayList<>(values.size() + 1);
        if (onServer) {
            PlayerRef player = host.roster() == null || from.isEmpty() ? null : host.roster().find(from);
            if (player == null) {
                reply.send(RemoteFunction.Answer.failed(remote.name() + " was invoked by someone who is not a player"));
                return;
            }
            args.add(Players.wrap(host, player));
        }
        args.addAll(values);
        Fiber fiber = host.engine() == null ? null : host.engine().fiber(fn);
        if (fiber == null) {
            Object[] out = host.call(fn, where, args.toArray());
            reply.send(out == null ? RemoteFunction.Answer.failed(where + " failed") : new RemoteFunction.Answer(true, Arrays.asList(out)));
            return;
        }
        boolean[] replied = {false};
        Fiber answering = new Fiber() {
            @Override
            public Suspend resume(Object... resumed) {
                Suspend next;
                try {
                    next = fiber.resume(resumed);
                } catch (RuntimeException e) {
                    replied[0] = true;
                    reply.send(RemoteFunction.Answer.failed(where + ": " + e.getMessage()));
                    throw e;
                }
                if (next == null && !replied[0]) {
                    replied[0] = true;
                    reply.send(new RemoteFunction.Answer(true, Arrays.asList(fiber.results())));
                }
                return next;
            }

            @Override
            public void cancel() {
                fiber.cancel();
                if (replied[0]) return;
                replied[0] = true;
                reply.send(RemoteFunction.Answer.failed(where + " stopped before it answered"));
            }
        };
        host.scheduler().start(answering, owner, args.toArray());
    }

    private static Suspend call(RemoteFunction remote, String from, String side, IntConsumer send) {
        Object[][] settled = {null};
        int call = remote.await(from, answer -> settled[0] = answer.ok()
                ? answer.values().toArray()
                : Suspend.failed("%s", answer.values().isEmpty() ? remote.name() + " failed on " + side : String.valueOf(answer.values().getFirst())));
        try {
            send.accept(call);
        } catch (IllegalArgumentException e) {
            remote.forget(call);
            throw new HostError("%s", e.getMessage());
        }
        double[] waited = {0};
        return new Suspend(dt -> {
            if (settled[0] != null) return settled[0];
            if (!remote.isAlive()) return Suspend.failed("%s was destroyed before %s answered", remote.name(), side);
            waited[0] += dt;
            if (remote.timeout > 0 && waited[0] >= remote.timeout) {
                remote.forget(call);
                return Suspend.failed("%s got no answer from %s within %s seconds", remote.name(), side, trim(remote.timeout));
            }
            return null;
        }, () -> remote.forget(call));
    }

    private static String trim(double seconds) {
        return seconds == Math.rint(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
    }

    private static RemoteFunction remote(Args a) {
        if (!(a.get(0) instanceof RemoteFunction remote)) throw a.error("expects to be called with ':' on a RemoteFunction");
        if (!remote.isAlive()) throw new HostError("remote function '%s' was destroyed, look it up again", remote.name());
        return remote;
    }

    private static InvokeRef invoke(Host host) {
        if (host.invoke() == null) throw new HostError("no transport bound");
        return host.invoke();
    }

    private static List<Object> declared(RemoteFunction remote, Args a, int first) {
        List<Object> args = new ArrayList<>(Arrays.asList(a.from(first)));
        try {
            Schema.check(remote.name(), remote.accepts, args);
        } catch (IllegalArgumentException e) {
            throw new HostError("%s", e.getMessage());
        }
        return args;
    }
}
