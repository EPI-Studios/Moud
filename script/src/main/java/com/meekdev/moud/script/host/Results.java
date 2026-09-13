package com.meekdev.moud.script.host;

public record Results(Object... values) {

    public static final Results NONE = new Results();

    public static Results of(Object... values) {
        return new Results(values);
    }
}
