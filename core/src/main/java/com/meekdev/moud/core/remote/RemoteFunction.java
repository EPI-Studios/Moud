package com.meekdev.moud.core.remote;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.instance.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class RemoteFunction extends Instance {

    public String accepts = "";

    @Prop(min = 0) public double timeout = 30;

    public final Callback onServerInvoke = new Callback();

    public final Callback onClientInvoke = new Callback();

    public record Answer(boolean ok, List<Object> values) {

        public static Answer failed(String why) {
            return new Answer(false, List.of(why));
        }
    }

    @FunctionalInterface
    public interface Reply {
        void send(Answer answer);
    }

    private record Waiting(String from, Consumer<Answer> then) {}

    private final Map<Integer, Waiting> waiting = new HashMap<>();
    private int nextCall = 1;

    public int await(String from, Consumer<Answer> then) {
        int call = nextCall++;
        waiting.put(call, new Waiting(from, then));
        return call;
    }

    public void forget(int call) {
        waiting.remove(call);
    }

    public void answer(int call, String from, Answer answer) {
        Waiting one = waiting.get(call);
        if (one == null || !one.from().equals(from)) return;
        waiting.remove(call);
        one.then().accept(answer);
    }
}
