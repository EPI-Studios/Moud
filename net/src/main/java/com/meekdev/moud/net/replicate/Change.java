package com.meekdev.moud.net.replicate;

// what one side has to tell the other. deliberately small: a tree is a stream of these
public sealed interface Change {

    record Reset() implements Change {}

    record Created(int id, String className, int parent, String name) implements Change {}

    record Wrote(int id, int property, Object value) implements Change {}

    // a reparent, which no property carries: the instance is the same one, hanging somewhere else
    record Moved(int id, int parent) implements Change {}

    record Destroyed(int id) implements Change {}
}
