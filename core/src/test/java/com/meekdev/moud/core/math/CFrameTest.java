package com.meekdev.moud.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CFrameTest {

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-9, "x");
        assertEquals(expected.y(), actual.y(), 1e-9, "y");
        assertEquals(expected.z(), actual.z(), 1e-9, "z");
    }

    @Test
    void composingPutsTheOffsetInTheParentFrame() {
        CFrame base = new CFrame(new Vec3(10, 0, 0), Quat.euler(0, Math.PI / 2, 0));
        CFrame moved = base.mul(CFrame.at(0, 0, -1));
        // base looks down -x after a quarter turn, so forward one metre lands there
        assertVec(new Vec3(9, 0, 0), moved.position());
    }

    @Test
    void objectSpaceRoundTrips() {
        CFrame f = new CFrame(new Vec3(3, 4, 5), Quat.euler(0.3, 1.1, -0.7));
        Vec3 world = new Vec3(1, 2, 3);
        assertVec(world, f.pointToWorld(f.pointToObject(world)));
    }

    @Test
    void inverseUndoesTheFrame() {
        CFrame f = new CFrame(new Vec3(-2, 7, 0.5), Quat.euler(0.2, -2.0, 0.9));
        CFrame back = f.mul(f.inverse());
        assertVec(Vec3.ZERO, back.position());
        assertEquals(1.0, Math.abs(back.rotation().w()), 1e-9);
    }

    @Test
    void lookAtFacesTheTarget() {
        CFrame f = CFrame.lookAt(Vec3.ZERO, new Vec3(0, 0, -10));
        assertVec(Vec3.FORWARD, f.lookVector());
    }

    @Test
    void repeatedCompositionStaysNormalized() {
        CFrame f = CFrame.IDENTITY;
        CFrame step = CFrame.angles(0.01, 0.02, 0.03);
        for (int i = 0; i < 100000; i++) f = f.mul(step);
        assertEquals(1.0, f.rotation().lengthSq(), 1e-6, "drifted off unit length");
    }

    @Test
    void slerpTakesTheShortPath() {
        Quat a = Quat.euler(0, 0.1, 0);
        Quat b = Quat.euler(0, -0.1, 0);
        Quat mid = a.slerp(b, 0.5);
        assertTrue(Math.abs(mid.y()) < 1e-9, "midpoint should sit at zero yaw");
    }
}
