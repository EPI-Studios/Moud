package com.moud.plugin.api.entity;

import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.ui.PlayerOverlay;
import com.moud.plugin.api.ui.UIOverlayDefinition;
import com.moud.plugin.api.ui.UIOverlayInteraction;
import com.moud.plugin.api.ui.UIOverlayInteractionListener;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class PlayerOverlayImpl implements PlayerOverlay {
    private final PluginContext context;
    private final PlayerContext playerContext;
    private final Player owner;
    private final Logger logger;

    private Object overlayService;
    private Constructor<?> definitionCtor;
    private Method upsertMethod;
    private Method removeMethod;
    private Method clearMethod;
    private Method setJavaHandlerMethod;
    private Method elementIdGetter;
    private Method actionGetter;
    private Method payloadGetter;

    PlayerOverlayImpl(PluginContext context, PlayerContext playerContext, Player owner) {
        this.context = context;
        this.playerContext = playerContext;
        this.owner = owner;
        this.logger = context.logger();
    }

    @Override
    public String upsert(UIOverlayDefinition definition) {
        Object def = toPacketDefinition(definition);
        if (def == null) return null;
        Object service = resolveService();
        if (service == null) return null;
        try {
            ensureUpsertMethod(service.getClass());
            upsertMethod.invoke(service, playerContext.player(), List.of(def));
            return definition.id();
        } catch (Exception e) {
            logger.warn("Failed to upsert UI overlay component {}", definition.id(), e);
            return null;
        }
    }

    @Override
    public void upsertMany(Collection<UIOverlayDefinition> definitions) {
        Object service = resolveService();
        if (service == null || definitions == null || definitions.isEmpty()) return;
        List<Object> packetDefs = new ArrayList<>();
        for (UIOverlayDefinition def : definitions) {
            Object packetDef = toPacketDefinition(def);
            if (packetDef != null) {
                packetDefs.add(packetDef);
            }
        }
        if (packetDefs.isEmpty()) return;
        try {
            ensureUpsertMethod(service.getClass());
            upsertMethod.invoke(service, playerContext.player(), packetDefs);
        } catch (Exception e) {
            logger.warn("Failed to upsert {} UI overlay components", packetDefs.size(), e);
        }
    }

    @Override
    public void remove(String id) {
        if (id == null || id.isBlank()) return;
        removeMany(List.of(id));
    }

    @Override
    public void removeMany(Collection<String> ids) {
        Object service = resolveService();
        if (service == null || ids == null || ids.isEmpty()) return;
        try {
            ensureRemoveMethod(service.getClass());
            removeMethod.invoke(service, playerContext.player(), new ArrayList<>(ids));
        } catch (Exception e) {
            logger.warn("Failed to remove UI overlay components {}", ids, e);
        }
    }

    @Override
    public void clear() {
        Object service = resolveService();
        if (service == null) return;
        try {
            ensureClearMethod(service.getClass());
            clearMethod.invoke(service, playerContext.player());
        } catch (Exception e) {
            logger.warn("Failed to clear UI overlays for {}", playerContext.username(), e);
        }
    }

    @Override
    public void onInteraction(UIOverlayInteractionListener listener) {
        Object service = resolveService();
        if (service == null) return;
        try {
            ensureHandlerMethod(service.getClass());
            if (listener == null) {
                setJavaHandlerMethod.invoke(service, playerContext.player(), null);
                return;
            }
            BiConsumer<Object, Object> bridge = (player, packet) -> {
                UIOverlayInteraction interaction = toInteraction(packet);
                if (interaction != null) {
                    listener.onInteraction(owner, interaction);
                }
            };
            setJavaHandlerMethod.invoke(service, playerContext.player(), bridge);
        } catch (Exception e) {
            logger.warn("Failed to register UI interaction handler for {}", playerContext.username(), e);
        }
    }

    private Object resolveService() {
        if (overlayService != null) {
            return overlayService;
        }
        try {
            Class<?> serviceClass = Class.forName("com.moud.server.ui.UIOverlayService");
            overlayService = serviceClass.getMethod("getInstance").invoke(null);
            return overlayService;
        } catch (Exception e) {
            logger.warn(
                    "UIOverlayService is not available on the classpath",
                    e
            );
            return null;
        }
    }

    private Object toPacketDefinition(UIOverlayDefinition definition) {
        if (definition == null) {
            return null;
        }
        Object service = resolveService();
        if (service == null) return null;

        try {
            ensureDefinitionCtor(service.getClass());
            return definitionCtor.newInstance(
                    definition.id(),
                    definition.type(),
                    definition.parentId(),
                    definition.props()
            );
        } catch (Exception e) {
            logger.warn("Failed to convert UIOverlayDefinition {} to packet form", definition.id(), e);
            return null;
        }
    }

    private UIOverlayInteraction toInteraction(Object packet) {
        if (packet == null) return null;
        try {
            ensureInteractionAccessors(packet.getClass());
            String elementId = Objects.toString(elementIdGetter.invoke(packet), "");
            String action = Objects.toString(actionGetter.invoke(packet), "");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payloadGetter.invoke(packet);
            return new UIOverlayInteraction(elementId, action, data);
        } catch (Exception e) {
            logger.warn("Failed to convert UI interaction packet", e);
            return null;
        }
    }

    private void ensureDefinitionCtor(Class<?> serviceClass) throws Exception {
        if (definitionCtor != null) return;
        Class<?> packetsClass = Class.forName("com.moud.network.MoudPackets");
        Class<?> defClass = null;
        for (Class<?> nested : packetsClass.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("UIElementDefinition")) {
                defClass = nested;
                break;
            }
        }
        if (defClass == null) {
            throw new ClassNotFoundException("MoudPackets.UIElementDefinition not found");
        }
        definitionCtor = defClass.getDeclaredConstructor(String.class, String.class, String.class, Map.class);
    }

    private void ensureUpsertMethod(Class<?> serviceClass) throws Exception {
        if (upsertMethod == null) {
            upsertMethod = serviceClass.getMethod("upsert", net.minestom.server.entity.Player.class, List.class);
        }
    }

    private void ensureRemoveMethod(Class<?> serviceClass) throws Exception {
        if (removeMethod == null) {
            removeMethod = serviceClass.getMethod("remove", net.minestom.server.entity.Player.class, List.class);
        }
    }

    private void ensureClearMethod(Class<?> serviceClass) throws Exception {
        if (clearMethod == null) {
            clearMethod = serviceClass.getMethod("clear", net.minestom.server.entity.Player.class);
        }
    }

    private void ensureHandlerMethod(Class<?> serviceClass) throws Exception {
        if (setJavaHandlerMethod == null) {
            setJavaHandlerMethod = serviceClass.getMethod(
                    "setJavaInteractionHandler",
                    net.minestom.server.entity.Player.class,
                    BiConsumer.class
            );
        }
    }

    private void ensureInteractionAccessors(Class<?> packetClass) throws Exception {
        if (elementIdGetter != null && actionGetter != null && payloadGetter != null) {
            return;
        }
        elementIdGetter = packetClass.getMethod("elementId");
        actionGetter = packetClass.getMethod("action");
        payloadGetter = packetClass.getMethod("payload");
    }
}

// should be rewritten to use other stuff than timestamps