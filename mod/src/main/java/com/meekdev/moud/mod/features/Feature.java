package com.meekdev.moud.mod.features;

import java.util.Locale;

public enum Feature {
    SKY,
    CLOUDS,
    FOG,
    STARS,
    WEATHER,
    DAY_NIGHT_CYCLE,
    AMBIENT_LIGHT,

    TERRAIN,
    BLOCK_BREAKING,
    BLOCK_PLACING,
    BLOCK_ENTITIES,
    FLUIDS,
    MOBS,
    ITEM_DROPS,

    HUNGER,
    HEALTH_REGEN,
    FALL_DAMAGE,
    DROWNING,
    FIRE_DAMAGE,
    VANILLA_MOVEMENT,
    INVENTORY,
    CRAFTING,
    XP,
    DEATH_SCREEN,

    HOTBAR,
    HEALTH_BAR,
    HUNGER_BAR,
    XP_BAR,
    CROSSHAIR,
    EFFECT_ICONS,
    BOSS_BAR,
    CHAT,
    TAB_LIST,
    SCOREBOARD,
    HAND,
    PAUSE_MENU,
    DEBUG_SCREEN,
    TITLE_SCREEN,

    ENTITY_RENDERING,
    BLOB_SHADOWS,
    PLAYER_MODEL,
    NAME_TAGS,
    VANILLA_SOUNDS,
    VANILLA_MUSIC,

    TRACE;

    private final String key = key(name());

    public String key() {
        return key;
    }

    private static String key(String constant) {
        StringBuilder sb = new StringBuilder(constant.length());
        boolean up = false;
        for (int i = 0; i < constant.length(); i++) {
            char c = constant.charAt(i);
            if (c == '_') {
                up = true;
            } else if (up) {
                sb.append(c);
                up = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
