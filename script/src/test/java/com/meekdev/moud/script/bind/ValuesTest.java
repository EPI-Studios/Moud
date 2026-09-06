package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValuesTest {

    private Part floor;
    private Vm vm;

    @BeforeEach
    void setUp() {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        floor = Instances.create(Classes.PART, world, "floor");
        vm = new Vm();
        vm.bind(world);
    }

    @AfterEach
    void tearDown() {
        vm.close();
    }

    @Test
    void aVec3PropertyRoundTrips() {
        vm.run("t", "game.world.floor.size = vec3(2, 3, 4)");
        assertEquals(new Vec3(2, 3, 4), floor.size);
        vm.run("t", "y = game.world.floor.size.y");
        assertEquals(3.0, vm.number("y"));
    }

    @Test
    void aColorPropertyRoundTrips() {
        vm.run("t", "game.world.floor.color = color(0.25, 0.5, 0.75)");
        assertEquals(new Color(0.25f, 0.5f, 0.75f, 1.0f), floor.color);
    }

    @Test
    void vec3HasArithmetic() {
        vm.run("t", "v = vec3(1, 2, 3) + vec3(1, 1, 1) - vec3(0, 1, 0)\nx = v.x + v.y + v.z");
        assertEquals(8.0, vm.number("x"));
    }

    @Test
    void aScalarMultipliesFromEitherSide() {
        vm.run("t", "a = (vec3(1, 2, 3) * 2).y\nb = (2 * vec3(1, 2, 3)).y");
        assertEquals(4.0, vm.number("a"));
        assertEquals(4.0, vm.number("b"));
    }

    @Test
    void valuesCompareByValueNotIdentity() {
        vm.run("t", "same = vec3(1, 2, 3) == vec3(1, 2, 3)");
        assertTrue(vm.bool("same"));
    }

    @Test
    void magnitudeAndUnitAreProperties() {
        vm.run("t", "m = vec3(0, 3, 4).magnitude\nu = vec3(0, 3, 4).unit.z");
        assertEquals(5.0, vm.number("m"));
        assertEquals(0.8, vm.number("u"), 1e-9);
    }

    @Test
    void aCframePropertyRoundTrips() {
        vm.run("t", "game.world.floor.cframe = cframe(1, 2, 3)");
        assertEquals(new Vec3(1, 2, 3), floor.cframe.position());
        vm.run("t", "y = game.world.floor.cframe.position.y");
        assertEquals(2.0, vm.number("y"));
    }

    @Test
    void cframeHasIdentityAndConstructors() {
        vm.run("t", "a = cframe.identity.position.x\nb = cframe(vec3(4, 5, 6)).position.z");
        assertEquals(0.0, vm.number("a"));
        assertEquals(6.0, vm.number("b"));
    }

    @Test
    void worldCframeComposesTheParentChain() {
        vm.run("t", "game.world.cframe = cframe(10, 0, 0)\ngame.world.floor.cframe = cframe(1, 0, 0)");
        vm.run("t", "x = game.world.floor.worldCframe.position.x");
        assertEquals(11.0, vm.number("x"));
    }

    @Test
    void writingWorldCframeStoresTheLocalOne() {
        vm.run("t", "game.world.cframe = cframe(10, 0, 0)\ngame.world.floor.worldCframe = cframe(12, 0, 0)");
        assertEquals(2.0, floor.cframe.position().x(), 1e-9);
    }

    @Test
    void anUnknownMemberIsAnError() {
        assertThrows(RuntimeException.class, () -> vm.run("t", "x = vec3(1, 2, 3).w"));
    }
}
