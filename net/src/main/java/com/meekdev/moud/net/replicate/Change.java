package com.meekdev.moud.net.replicate;

public sealed interface Change {

    record Reset() implements Change {}

    record Created(int id, String className, int parent, String name) implements Change {}

    record Wrote(int id, int property, Object value) implements Change {}

    record Moved(int id, int parent) implements Change {}

    record Destroyed(int id) implements Change {}

    record Tagged(int id, String tag, boolean added) implements Change {}
}
