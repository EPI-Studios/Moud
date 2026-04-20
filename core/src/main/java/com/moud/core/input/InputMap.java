package com.moud.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InputMap {

    private final Map<String, InputAction> actions = new LinkedHashMap<>();
    private final Map<String, List<InputBinding>> bindings = new LinkedHashMap<>();

    public InputMap() { }

    public static InputMap withDefaults() {
        InputMap map = new InputMap();
        for (InputAction action : CoreInputActions.defaults().values()) {
            map.registerAction(action);
        }
        return map;
    }

    public void registerAction(InputAction action) {
        if (action == null || action.id() == null || action.id().isBlank()) return;
        actions.put(action.id(), action);
        if (!bindings.containsKey(action.id())) {
            bindings.put(action.id(), new ArrayList<>(action.defaultBindings()));
        }
    }

    public boolean hasAction(String id) {
        return id != null && actions.containsKey(id);
    }

    public List<String> actionIds() {
        return List.copyOf(actions.keySet());
    }

    public InputAction getAction(String id) {
        return actions.get(id);
    }

    public List<InputBinding> getBindings(String actionId) {
        List<InputBinding> bs = bindings.get(actionId);
        return bs == null ? Collections.emptyList() : List.copyOf(bs);
    }

    public void setBindings(String actionId, List<InputBinding> newBindings) {
        if (!actions.containsKey(actionId)) return;
        bindings.put(actionId, newBindings == null ? new ArrayList<>() : new ArrayList<>(newBindings));
    }

    public void addBinding(String actionId, InputBinding binding) {
        if (!actions.containsKey(actionId) || binding == null) return;
        bindings.computeIfAbsent(actionId, k -> new ArrayList<>()).add(binding);
    }

    public void resetToDefaults(String actionId) {
        InputAction a = actions.get(actionId);
        if (a == null) return;
        bindings.put(actionId, new ArrayList<>(a.defaultBindings()));
    }

    public void resetAllToDefaults() {
        for (InputAction a : actions.values()) {
            bindings.put(a.id(), new ArrayList<>(a.defaultBindings()));
        }
    }

    public Map<String, List<InputBinding>> snapshotBindings() {
        LinkedHashMap<String, List<InputBinding>> snap = new LinkedHashMap<>();
        for (Map.Entry<String, List<InputBinding>> e : bindings.entrySet()) {
            snap.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return snap;
    }
}
