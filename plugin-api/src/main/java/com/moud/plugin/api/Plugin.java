package com.moud.plugin.api;

import com.moud.plugin.api.command.CommandDsl;
import com.moud.plugin.api.entity.Player;
import com.moud.plugin.api.event.ClientEventListener;
import com.moud.plugin.api.event.EventsDsl;
import com.moud.plugin.api.network.BroadcastBuilder;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.scheduler.SchedulerDsl;
import com.moud.plugin.api.world.WorldDsl;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Consumer;


public abstract class Plugin implements MoudPlugin {
    private final PluginDescription description;
    private PluginContext context;
    private WorldDsl world;
    private SchedulerDsl scheduler;
    private EventsDsl events;

    protected Plugin() {
        PluginDefinition definition = getClass().getAnnotation(PluginDefinition.class);
        if (definition == null) {
            throw new IllegalStateException("Plugins extending the high-level base must be annotated with @PluginDefinition");
        }
        this.description = new PluginDescription(
                definition.id(),
                definition.name(),
                definition.version(),
                definition.description()
        );
    }

    @Override
    public final PluginDescription description() {
        return description;
    }

    @Override
    public final void onLoad(PluginContext context) throws Exception {
        this.context = context;
        initializeFacades();
        onLoad();
    }

    @Override
    public final void onEnable(PluginContext context) throws Exception {
        this.context = context;
        initializeFacades();
        onEnable();
    }

    @Override
    public void onDisable() {
        // Allow subclasses to override if desired
    }

    protected void onLoad() throws Exception {
    }

    protected void onEnable() throws Exception {
    }

    /**
     * Access to services, data directory, and logging. Available once {@link #onLoad(PluginContext)} is invoked.
     */
    protected final PluginContext context() {
        return context;
    }

    /**
     * Shared plugin logger that tags output with the plugin id.
     */
    protected final Logger logger() {
        return context.logger();
    }

    /**
     * World DSL for spawning and querying objects.
     */
    protected final WorldDsl world() {
        return world;
    }

    /**
     * Scheduler used to enqueue delayed or repeating tasks.
     */
    protected final SchedulerDsl schedule() {
        return scheduler;
    }

    /**
     * Build and send a client event broadcast with the given name.
     */
    protected final BroadcastBuilder broadcast(String eventName) {
        return new BroadcastBuilder(context.network(), eventName);
    }

    /**
     * Begin building a command registered under the provided root name.
     */
    protected final CommandDsl command(String name) {
        return new CommandDsl(context, name);
    }

    /**
     * Register a server-side event listener.
     */
    protected final <T extends com.moud.plugin.api.events.PluginEvent> void on(Class<T> eventType,
                                                                               Consumer<T> listener) {
        events.on(eventType, listener);
    }

    /**
     * Register a client event listener by name (e.g. events emitted from scripts or UI).
     */
    protected final void onClient(String eventName, ClientEventListener listener) {
        events.onClient(eventName, listener);
    }

    /**
     * Wrap a raw {@link PlayerContext} into the {@link Player} facade.
     */
    protected final Player player(com.moud.plugin.api.player.PlayerContext playerContext) {
        return Player.wrap(context, playerContext);
    }

    private void initializeFacades() {
        Objects.requireNonNull(context, "PluginContext has not been provided yet");
        this.world = new WorldDsl(context);
        this.scheduler = new SchedulerDsl(context.scheduler());
        this.events = new EventsDsl(context.events());
    }
}
