package com.meekdev.moud.script.host;

import java.util.Collection;
import java.util.StringJoiner;

public final class Literals {

    private Literals() {}

    public static String union(Collection<String> names) {
        StringJoiner union = new StringJoiner(" | ");
        for (String name : names) union.add('"' + name + '"');
        return union.toString();
    }
}
