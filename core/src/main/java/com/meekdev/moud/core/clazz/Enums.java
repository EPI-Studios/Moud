package com.meekdev.moud.core.clazz;

import java.util.ArrayList;
import java.util.List;

// an enum is a string in luau, spelled the way a property is
//
// the java constant is SCREAMING_SNAKE because that is what java constants look like, and the
// place says "thirdPerson" because that is what every other name in the api looks like. the
// translation lives here so neither side has to hold the other's spelling
public final class Enums {

    private Enums() {}

    public static String name(Enum<?> value) {
        String constant = value.name();
        StringBuilder out = new StringBuilder(constant.length());
        boolean upper = false;
        for (int i = 0; i < constant.length(); i++) {
            char c = constant.charAt(i);
            if (c == '_') {
                upper = true;
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            upper = false;
        }
        return out.toString();
    }

    public static List<String> names(Class<?> type) {
        List<String> out = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) out.add(name((Enum<?>) constant));
        return out;
    }

    // a typo is an error that says what was allowed, because the alternative is a place that
    // silently keeps the default and a developer reading the same four lines for an hour
    public static Enum<?> parse(Class<?> type, String spelled) {
        for (Object constant : type.getEnumConstants()) {
            if (name((Enum<?>) constant).equals(spelled)) return (Enum<?>) constant;
        }
        throw new IllegalArgumentException(
                "'" + spelled + "' is not one of " + String.join(", ", names(type)));
    }
}
