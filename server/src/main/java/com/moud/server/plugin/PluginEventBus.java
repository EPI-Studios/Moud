package com.moud.server.plugin;

import com.moud.plugin.api.events.PlayerJoinEvent;
import com.moud.plugin.api.events.PlayerLeaveEvent;
import com.moud.plugin.api.events.PluginEvent;
import com.moud.plugin.api.events.ScriptEvent;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.events.EventListener;
import com.moud.plugin.api.services.events.Subscription;
import com.moud.server.plugin.player.PlayerContextImpl;
import net.minestom.server.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PluginEventBus {
    private static final PluginEventBus INSTANCE = new PluginEventBus();

    private final Map<Class<? extends PluginEvent>, CopyOnWriteArrayList<ListenerHolder<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, CopyOnWriteArrayList<SubscriptionImpl<?>>> subscriptionsByOwner = new ConcurrentHashMap<>();

    private PluginEventBus() {
    }

    public static PluginEventBus getInstance() {
        return INSTANCE;
    }

    public <T extends PluginEvent> Subscription subscribe(Object owner, Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");

        ListenerHolder<T> holder = new ListenerHolder<>(eventType, listener, owner);
        listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(holder);

        SubscriptionImpl<T> subscription = new SubscriptionImpl<>(this, holder);
        subscriptionsByOwner.computeIfAbsent(owner, key -> new CopyOnWriteArrayList<>()).add(subscription);
        return subscription;
    }

    void unsubscribe(SubscriptionImpl<?> subscription) {
        ListenerHolder<?> holder = subscription.holder;
        CopyOnWriteArrayList<ListenerHolder<?>> holders = listeners.get(holder.eventType);
        if (holders != null) {
            holders.remove(holder);
            if (holders.isEmpty()) {
                listeners.remove(holder.eventType, holders);
            }
        }
        CopyOnWriteArrayList<SubscriptionImpl<?>> owned = subscriptionsByOwner.get(holder.owner);
        if (owned != null) {
            owned.remove(subscription);
            if (owned.isEmpty()) {
                subscriptionsByOwner.remove(holder.owner);
            }
        }
    }

    public void unregisterOwner(Object owner) {
        CopyOnWriteArrayList<SubscriptionImpl<?>> owned = subscriptionsByOwner.remove(owner);
        if (owned != null) {
            owned.forEach(SubscriptionImpl::internalUnsubscribe);
            owned.clear();
        }
    }

    public void dispatchPlayerJoin(Player player) {
        dispatch(new PlayerJoinEvent(Instant.now(), new PlayerContextImpl(player)));
    }

    public void dispatchPlayerLeave(Player player) {
        dispatch(new PlayerLeaveEvent(Instant.now(), new PlayerContextImpl(player)));
    }

    public void dispatchScriptEvent(String eventName, Player player, String payload) {
        PlayerContext ctx = new PlayerContextImpl(player);
        dispatch(new ScriptEvent(Instant.now(), ctx, eventName, payload));
    }

    public void dispatch(PluginEvent event) {
        listeners.forEach((type, holderList) -> {
            if (!type.isAssignableFrom(event.getClass())) {
                return;
            }
            for (ListenerHolder<?> holder : holderList) {
                holder.tryInvoke(event);
            }
        });
    }

    private record ListenerHolder<T extends PluginEvent>(Class<T> eventType,
                                                         EventListener<T> listener,
                                                         Object owner) {
        @SuppressWarnings("unchecked")
        void tryInvoke(PluginEvent event) {
            if (eventType.isInstance(event)) {
                listener.handle(eventType.cast(event));
            }
        }
    }

    private static final class SubscriptionImpl<T extends PluginEvent> implements Subscription {
        private final PluginEventBus bus;
        private final ListenerHolder<T> holder;
        private volatile boolean active = true;

        private SubscriptionImpl(PluginEventBus bus, ListenerHolder<T> holder) {
            this.bus = bus;
            this.holder = holder;
        }

        @Override
        public void unsubscribe() {
            if (!active) {
                return;
            }
            active = false;
            bus.unsubscribe(this);
        }

        void internalUnsubscribe() {
            active = false;
            bus.listeners.computeIfPresent(holder.eventType, (type, list) -> {
                list.remove(holder);
                return list.isEmpty() ? null : list;
            });
        }

        @Override
        public boolean active() {
            return active;
        }
    }
}
