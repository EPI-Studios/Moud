package com.meekdev.moud.core.interp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.math.Vec3;
import org.junit.jupiter.api.Test;

class InterpTest {

    @Test
    void stepHoldsUntilTheEnd() {
        assertEquals(0.0, Curve.STEP.at(0.99));
        assertEquals(1.0, Curve.STEP.at(1.0));
    }

    @Test
    void curvesStartAtZeroAndEndAtOne() {
        for (Curve c : Curve.values()) {
            assertEquals(0.0, c.at(0.0), 1e-9, c + " at 0");
            assertEquals(1.0, c.at(1.0), 1e-9, c + " at 1");
        }
    }

    @Test
    void typesWithNoMidpointStep() {
        assertFalse(Blend.isContinuous(PropertyType.STRING));
        assertFalse(Blend.isContinuous(PropertyType.BOOL));
        assertTrue(Blend.isContinuous(PropertyType.VEC3));
        assertEquals("a", Blend.of(PropertyType.STRING, "a", "b", 0.9));
        assertEquals("b", Blend.of(PropertyType.STRING, "a", "b", 1.0));
    }

    @Test
    void aTickWrittenValueSmoothsAcrossTheFramesBetween() {
        Track track = new Track(PropertyType.VEC3, Vec3.ZERO);
        track.advance(0.05);
        track.write(new Vec3(10, 0, 0));

        track.advance(0.025);
        assertEquals(5.0, ((Vec3) track.sample()).x(), 1e-9, "half a tick in, half way there");
        track.advance(0.025);
        assertEquals(10.0, ((Vec3) track.sample()).x(), 1e-9);
    }

    @Test
    void aFrameWrittenValueHasNoLag() {
        Track track = new Track(PropertyType.VEC3, Vec3.ZERO);
        track.advance(0.008);
        track.write(new Vec3(1, 0, 0));
        track.advance(0.008);
        assertEquals(1.0, ((Vec3) track.sample()).x(), 1e-9, "a frame long window is already done");
    }

    @Test
    void samplingHoldsRatherThanExtrapolating() {
        Track track = new Track(PropertyType.VEC3, Vec3.ZERO);
        track.advance(0.05);
        track.write(new Vec3(10, 0, 0));
        track.advance(5.0);
        assertEquals(10.0, ((Vec3) track.sample()).x(), 1e-9, "no overshoot when nothing new arrives");
    }

    @Test
    void aWriteMidFlightStartsFromWhereItGotTo() {
        Track track = new Track(PropertyType.VEC3, Vec3.ZERO);
        track.advance(0.05);
        track.write(new Vec3(10, 0, 0));
        track.advance(0.025);
        track.write(new Vec3(0, 0, 0));
        assertEquals(5.0, ((Vec3) track.sample()).x(), 1e-9, "the new leg starts at the old sample");
    }
}
