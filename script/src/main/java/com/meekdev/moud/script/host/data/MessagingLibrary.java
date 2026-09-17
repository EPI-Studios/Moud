package com.meekdev.moud.script.host.data;

import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessagingLibrary {

    private static final int LONGEST = 1024;

    private MessagingLibrary() {}

    public static void install(Host host) {
        Map<String, List<Listener>> topics = new HashMap<>();
        host.api().declare(new Members("Subscription")
                .method("unsubscribe", "() -> ()", a -> null).decl());
        Members messaging = new Members("Messaging")
                .method("publish", "(topic: string, message: any) -> ()", a -> {
                    String topic = topic(a.string(1));
                    Object message = a.get(2);
                    for (Listener listener : List.copyOf(topics.getOrDefault(topic, List.of()))) {
                        if (!listener.gone) host.call(listener.handler, "messaging " + topic, message);
                    }
                    return null;
                })
                .method("subscribe", "(topic: string, handler: (message: any) -> ()) -> Subscription", a -> {
                    String topic = topic(a.string(1));
                    Listener listener = new Listener(a.callable(2).retain());
                    topics.computeIfAbsent(topic, name -> new ArrayList<>()).add(listener);
                    host.ownership().onRelease(host.ownership().current(), () -> drop(topics, topic, listener));
                    return new Members("Subscription").method("unsubscribe", "() -> ()", inner -> {
                        drop(topics, topic, listener);
                        return null;
                    });
                });
        host.global("messaging", "Messaging", messaging);
        host.declare(messaging);
    }

    private static void drop(Map<String, List<Listener>> topics, String topic, Listener listener) {
        if (listener.gone) return;
        listener.gone = true;
        listener.handler.release();
        List<Listener> listeners = topics.get(topic);
        if (listeners != null) listeners.remove(listener);
    }

    private static String topic(String name) {
        if (name.isEmpty() || name.length() > LONGEST) throw new HostError("a topic needs a name of up to %d letters", LONGEST);
        return name;
    }

    private static final class Listener {
        private final Callable handler;
        private boolean gone;

        private Listener(Callable handler) {
            this.handler = handler;
        }
    }
}
