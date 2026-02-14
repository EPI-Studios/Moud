package com.moud.core.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public final class ResPathTest {
    @Test
    void normalizesLeadingSlashesAndDoubleSlashes() {
        ResPath path = new ResPath("res:////foo//bar/");
        assertEquals("res://foo/bar", path.value());
    }

    @Test
    void rejectsRelativeSegments() {
        assertFalse(ResPath.validate("res://foo/..").ok());
        assertThrows(IllegalArgumentException.class, () -> new ResPath("res://foo/.."));
    }
}

