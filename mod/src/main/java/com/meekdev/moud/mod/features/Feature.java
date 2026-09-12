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

    // one row a frame of everything behind your own body, to a file, while it stands on something
    // that moves
    //
    // a shake is the one bug a screenshot cannot show and a sentence cannot pin down. this wrote the
    // lot and the answer was in the shape of one column: the extrema of a cubic's overshoot sit at
    // 0.211 and 0.789 through a tick, and that is where they were. off by default, because a thing
    // that writes files unasked is not a thing to ship on
    TRACE;

    private final String key = key(name());

    // the name a place writes in place.toml, blockBreaking rather than BLOCK_BREAKING
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
