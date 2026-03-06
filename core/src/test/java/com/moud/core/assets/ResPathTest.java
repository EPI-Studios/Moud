package com.moud.core.assets;

import static Assertions.assertEquals;
import static Assertions.assertFalse;
import static Assertions.assertThrows;

import org.junit.jupiter.api.Assertions;

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

