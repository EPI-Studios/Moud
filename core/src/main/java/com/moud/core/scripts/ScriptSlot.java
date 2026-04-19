package com.moud.core.scripts;

public enum ScriptSlot {
    SCRIPT("script"),
    CLIENT_SCRIPT("client_script");

    private final String propertyKey;

    ScriptSlot(String propertyKey) {
        this.propertyKey = propertyKey;
    }

    public String propertyKey() {
        return propertyKey;
    }

    public static ScriptSlot fromPropertyKey(String key) {
        if (key == null) return null;
        for (ScriptSlot slot : values()) {
            if (slot.propertyKey.equals(key)) return slot;
        }
        return null;
    }

    public boolean accepts(ScriptSide side) {
        if (side == null) return false;
        return switch (this) {
            case SCRIPT -> side.runsOnServer();
            case CLIENT_SCRIPT -> side.runsOnClient();
        };
    }
}
