package com.moud.client.util;

import org.graalvm.polyglot.Value;

public final class PolyglotValueUtil {
    private PolyglotValueUtil() {
    }

    public static double asDouble(Value value, double fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            if (value.fitsInInt()) {
                return value.asInt();
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    public static float asFloat(Value value, float fallback) {
        double d = asDouble(value, fallback);
        if (!Double.isFinite(d)) {
            return fallback;
        }
        return (float) d;
    }

    public static int asInt(Value value, int fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                long asLong = value.asLong();
                if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
                    return (int) asLong;
                }
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    public static long asLong(Value value, long fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            if (value.fitsInLong()) {
                return value.asLong();
            }
            if (value.fitsInInt()) {
                return value.asInt();
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    public static boolean asBoolean(Value value, boolean fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.fitsInInt()) {
                return value.asInt() != 0;
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    public static String asString(Value value, String fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            if (value.isString()) {
                return value.asString();
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    public static double readDouble(Value obj, String member, double fallback) {
        return asDouble(getMember(obj, member), fallback);
    }

    public static float readFloat(Value obj, String member, float fallback) {
        return asFloat(getMember(obj, member), fallback);
    }

    public static int readInt(Value obj, String member, int fallback) {
        return asInt(getMember(obj, member), fallback);
    }

    public static long readLong(Value obj, String member, long fallback) {
        return asLong(getMember(obj, member), fallback);
    }

    public static boolean readBoolean(Value obj, String member, boolean fallback) {
        return asBoolean(getMember(obj, member), fallback);
    }

    public static String readString(Value obj, String member, String fallback) {
        return asString(getMember(obj, member), fallback);
    }

    public static Value getMember(Value obj, String member) {
        if (obj == null || obj.isNull() || member == null || member.isBlank() || !obj.hasMember(member)) {
            return null;
        }
        try {
            return obj.getMember(member);
        } catch (Exception ignored) {
            return null;
        }
    }
}
