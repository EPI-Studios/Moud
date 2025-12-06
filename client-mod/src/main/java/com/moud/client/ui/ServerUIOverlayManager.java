package com.moud.client.ui;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.api.service.UIService;
import com.moud.client.ui.component.*;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ServerUIOverlayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerUIOverlayManager.class);
    private static final ServerUIOverlayManager INSTANCE = new ServerUIOverlayManager();

    private static final String PROP_X = "x";
    private static final String PROP_Y = "y";
    private static final String PROP_WIDTH = "width";
    private static final String PROP_HEIGHT = "height";
    private static final String PROP_VISIBLE = "visible";
    private static final String PROP_OPACITY = "opacity";
    private static final String PROP_BG_COLOR = "backgroundColor";
    private static final String PROP_BG = "background";
    private static final String PROP_TEXT_COLOR = "textColor";
    private static final String PROP_TEXT_ALIGN = "textAlign";
    private static final String PROP_PADDING = "padding";
    private static final String PROP_ANCHOR = "anchor";
    private static final String PROP_SCALE = "scale";
    private static final String PROP_BORDER_COLOR = "borderColor";
    private static final String PROP_BORDER_WIDTH = "borderWidth";
    private static final String PROP_FULLSCREEN = "fullscreen";

    private final Map<String, UIComponent> components = new ConcurrentHashMap<>();
    private final Map<String, String> parentLookup = new ConcurrentHashMap<>();

    private ServerUIOverlayManager() {
    }

    public static ServerUIOverlayManager getInstance() {
        return INSTANCE;
    }


    public void upsert(List<MoudPackets.UIElementDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) return;
        MinecraftClient.getInstance().execute(() -> upsertInternal(definitions));
    }

    public void remove(List<String> elementIds) {
        if (elementIds == null || elementIds.isEmpty()) return;

        MinecraftClient.getInstance().execute(() -> {
            UIService uiService = getUIService();
            if (uiService == null) return;

            elementIds.stream()
                    .filter(Objects::nonNull)
                    .forEach(id -> removeComponentAndChildren(id, uiService));
        });
    }

    public void clear() {
        MinecraftClient.getInstance().execute(() -> {
            UIService uiService = getUIService();
            if (uiService == null) return;

            components.keySet().forEach(uiService::removeElement);
            components.clear();
            parentLookup.clear();
        });
    }


    private void upsertInternal(List<MoudPackets.UIElementDefinition> definitions) {
        UIService uiService = getUIService();
        if (uiService == null) {
            LOGGER.debug("UIService unavailable");
            return;
        }

        List<UIComponent> processedComponents = new ArrayList<>();

        for (MoudPackets.UIElementDefinition def : definitions) {
            if (!isValidDefinition(def)) continue;

            UIComponent component = getOrCreateComponent(def, uiService);
            if (component == null) continue;

            applyProperties(component, def.props());
            updateParentLookup(def);
            processedComponents.add(component);
        }

        for (UIComponent component : processedComponents) {
            reparentComponent(component, parentLookup.get(component.getComponentId()));
        }
    }

    private boolean isValidDefinition(MoudPackets.UIElementDefinition def) {
        return def != null && def.id() != null && !def.id().isBlank() && def.type() != null;
    }

    private UIService getUIService() {
        return ClientAPIService.INSTANCE != null ? ClientAPIService.INSTANCE.ui : null;
    }

    private UIComponent getOrCreateComponent(MoudPackets.UIElementDefinition def, UIService uiService) {
        if (components.containsKey(def.id())) {
            UIComponent existing = components.get(def.id());
            existing.setServerControlled(true);
            return existing;
        }

        UIComponent component = createComponentByType(def.type(), uiService);
        if (component == null) {
            LOGGER.warn("Unknown UI component type '{}'", def.type());
            return null;
        }

        component.setComponentId(def.id());
        component.setServerControlled(true);

        boolean isRoot = def.parentId() == null || def.parentId().isBlank();
        component = uiService.registerComponent(component, isRoot);

        components.put(def.id(), component);
        return component;
    }

    private UIComponent createComponentByType(String type, UIService uiService) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "text" -> new UIText("", uiService);
            case "button" -> new UIButton("", uiService);
            case "input" -> new UIInput("", uiService);
            case "image" -> new UIImage("", uiService);
            case "container" -> new UIContainer(uiService);
            default -> null;
        };
    }

    private void updateParentLookup(MoudPackets.UIElementDefinition def) {
        if (def.parentId() == null || def.parentId().isBlank()) {
            parentLookup.remove(def.id());
        } else {
            parentLookup.put(def.id(), def.parentId());
        }
    }

    private void removeComponentAndChildren(String id, UIService uiService) {
        UIComponent component = components.remove(id);
        parentLookup.remove(id);

        if (component != null) {
            if (component.parent != null) {
                component.parent.removeChild(component);
            }
            UIOverlayManager.getInstance().removeOverlayElement(component);
        }

        // Find and remove children recursively
        List<String> children = parentLookup.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), id))
                .map(Map.Entry::getKey)
                .toList();

        children.forEach(childId -> removeComponentAndChildren(childId, uiService));
        uiService.removeElement(id);
    }

    private void reparentComponent(UIComponent component, String parentId) {
        if (component == null) return;

        // if root element
        if (parentId == null || parentId.isBlank()) {
            detachFromCurrentParent(component);
            UIOverlayManager.getInstance().addOverlayElement(component);
            return;
        }

        // if child element
        UIComponent newParent = components.get(parentId);
        if (newParent == null) {
            LOGGER.debug("Parent {} not found for component {}", parentId, component.getComponentId());
            return;
        }

        if (component.parent != newParent) {
            detachFromCurrentParent(component);
            newParent.appendChild(component);
        }

        UIOverlayManager.getInstance().removeOverlayElement(component);
    }

    private void detachFromCurrentParent(UIComponent component) {
        if (component.parent != null) {
            component.parent.removeChild(component);
        }
    }


    private void applyProperties(UIComponent component, Map<String, Object> props) {
        if (props == null || props.isEmpty()) return;

        applyCommonProperties(component, props);

        if (component instanceof UIText t) applyTextProperties(t, props);
        else if (component instanceof UIButton b) applyButtonProperties(b, props);
        else if (component instanceof UIInput i) applyInputProperties(i, props);
        else if (component instanceof UIImage img) applyImageProperties(img, props);
        else if (component instanceof UIContainer c) applyContainerProperties(c, props);
    }

    private void applyCommonProperties(UIComponent c, Map<String, Object> props) {
        ifPresent(props, PROP_FULLSCREEN, v -> c.setFullscreen(toBoolean(v, false)));
        ifPresent(props, PROP_VISIBLE, v -> {
            if (toBoolean(v, true)) c.show();
            else c.hide();
        });

        // dimensions and position
        ifPresent(props, PROP_X, v -> c.setX(toInt(v, (int) c.getX())));
        ifPresent(props, PROP_Y, v -> c.setY(toInt(v, (int) c.getY())));
        ifPresent(props, PROP_WIDTH, v -> c.setWidth(toInt(v, (int) c.getWidth())));
        ifPresent(props, PROP_HEIGHT, v -> c.setHeight(toInt(v, (int) c.getHeight())));

        // style
        ifPresent(props, PROP_BG, v -> c.setBackgroundColor(toString(v, c.getBackgroundColor())));
        ifPresent(props, PROP_BG_COLOR, v -> c.setBackgroundColor(toString(v, c.getBackgroundColor())));
        ifPresent(props, PROP_TEXT_COLOR, v -> c.setTextColor(toString(v, c.getTextColor())));
        ifPresent(props, PROP_OPACITY, v -> c.setOpacity(toDouble(v, c.getOpacity())));
        ifPresent(props, PROP_TEXT_ALIGN, v -> c.setTextAlign(toString(v, c.getTextAlign())));
        ifPresent(props, PROP_ANCHOR, v -> c.setAnchor(toString(v, "top_left")));
        ifPresent(props, PROP_PADDING, v -> applyPadding(c, v));

        // other properties
        if (props.containsKey("borderColor") || props.containsKey("borderWidth")) {
            int width = toInt(props.get(PROP_BORDER_WIDTH), c.getBorderWidth());
            String color = toString(props.get(PROP_BORDER_COLOR), c.getBorderColor());
            c.setBorder(width, color);
        }

        if (props.containsKey("relativeTo")) {
            String targetId = toString(props.get("relativeTo"), null);
            String position = toString(props.getOrDefault("relativePosition", "below"), "below");
            if (targetId != null) c.relativeTo(targetId, position);
        }

        if (props.containsKey(PROP_SCALE)) {
            Object scale = props.get(PROP_SCALE);
            if (scale instanceof Map<?, ?> m) {
                c.setScale(toDouble(m.get("x"), 1.0), toDouble(m.get("y"), 1.0));
            } else {
                double s = toDouble(scale, 1.0);
                c.setScale(s, s);
            }
        }
    }

    private void applyTextProperties(UIText t, Map<String, Object> props) {
        ifPresent(props, "text", v -> t.setText(toString(v, t.getText())));
        ifPresent(props, "content", v -> t.setText(toString(v, t.getText())));
    }

    private void applyButtonProperties(UIButton b, Map<String, Object> props) {
        ifPresent(props, "text", v -> b.setText(toString(v, b.getText())));
        ifPresent(props, "label", v -> b.setText(toString(v, b.getText())));
    }

    private void applyInputProperties(UIInput i, Map<String, Object> props) {
        ifPresent(props, "value", v -> i.setValue(toString(v, i.getValue())));
    }

    private void applyImageProperties(UIImage i, Map<String, Object> props) {
        ifPresent(props, "source", v -> i.setSource(toString(v, i.getSource())));
    }

    private void applyContainerProperties(UIContainer c, Map<String, Object> props) {
        ifPresent(props, "flexDirection", v -> c.setFlexDirection(toString(v, c.getFlexDirection())));
        ifPresent(props, "justifyContent", v -> c.setJustifyContent(toString(v, c.getJustifyContent())));
        ifPresent(props, "alignItems", v -> c.setAlignItems(toString(v, c.getAlignItems())));
        ifPresent(props, "gap", v -> c.setGap(toDouble(v, c.getGap())));
        ifPresent(props, "autoResize", v -> c.setAutoResize(toBoolean(v, true)));
    }

    private void applyPadding(UIComponent c, Object padding) {
        if (padding instanceof List<?> list && list.size() >= 4) {
            c.setPadding(
                    toDouble(list.get(0), c.getPaddingTop()),
                    toDouble(list.get(1), c.getPaddingRight()),
                    toDouble(list.get(2), c.getPaddingBottom()),
                    toDouble(list.get(3), c.getPaddingLeft())
            );
        } else if (padding instanceof Number n) {
            double val = n.doubleValue();
            c.setPadding(val, val, val, val);
        } else if (padding instanceof Map<?, ?> m) {
            c.setPadding(
                    toDouble(m.get("top"), c.getPaddingTop()),
                    toDouble(m.get("right"), c.getPaddingRight()),
                    toDouble(m.get("bottom"), c.getPaddingBottom()),
                    toDouble(m.get("left"), c.getPaddingLeft())
            );
        }
    }


    private void ifPresent(Map<String, Object> props, String key, Consumer<Object> action) {
        Object value = props.get(key);
        if (value != null) action.accept(value);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value != null) {
            String str = value.toString().trim().toLowerCase(Locale.ROOT);
            return switch (str) {
                case "true", "yes", "1" -> true;
                case "false", "no", "0" -> false;
                default -> fallback;
            };
        }
        return fallback;
    }

    private String toString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}