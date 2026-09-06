package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxiesTest {

    private InstanceTree tree;
    private Instance world;
    private Part floor;
    private Vm vm;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        floor = Instances.create(Classes.PART, world, "floor");
        vm = new Vm();
        vm.bind(world, Classes.registry());
    }

    @AfterEach
    void tearDown() {
        vm.close();
    }

    @Test
    void readsAPropertyThroughTheClassDef() {
        vm.run("t", "t = game.world.floor.transparency");
        assertEquals(0.0, vm.number("t"));
    }

    @Test
    void writingAPropertyGoesThroughTheMutationPath() {
        vm.run("t", "game.world.floor.transparency = 0.25");
        assertEquals(0.25, floor.transparency);
        assertEquals(1, tree.dirtyCount(), "one dirty entry, same as a java write");
    }

    @Test
    void aChildIsReachedByName() {
        vm.run("t", "n = game.world.floor.name");
        assertEquals("floor", vm.text("n"));
    }

    @Test
    void theSameInstanceIsTheSameProxy() {
        vm.run("t", "same = game.world.floor == game.world.floor");
        assertTrue(vm.bool("same"));
    }

    @Test
    void anUnknownMemberIsAnError() {
        assertThrows(RuntimeException.class,
                () -> vm.run("t", "x = game.world.floor.wobble"));
    }

    @Test
    void writingAnUnknownPropertyIsAnError() {
        assertThrows(RuntimeException.class,
                () -> vm.run("t", "game.world.floor.wobble = 1"));
    }

    @Test
    void aDestroyedInstanceIsAnError() {
        vm.run("t", "part = game.world.floor");
        Instances.destroy(floor);
        assertThrows(RuntimeException.class, () -> vm.run("t", "x = part.transparency"));
    }
}
