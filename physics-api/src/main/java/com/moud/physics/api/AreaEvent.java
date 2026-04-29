package com.moud.physics.api;

public record AreaEvent(BodyHandle area, BodyHandle other, boolean entered) {}
