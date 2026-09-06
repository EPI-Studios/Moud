package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class AddTest {

    private InstanceTree tree;
    private Instance world;
    private Vm vm;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        vm = new Vm();
        vm.bind(world, Classes.registry());
    }

    @AfterEach
    void tearDown() {
        vm.close();
    }

    @Test
    void theSnippetFromTheDesignRuns() {
        vm.run("main", """
                local world = game.world
                local floor = world:add("Part", {
                    size     = vec3(60, 1, 60),
                    position = vec3(0, 0, 0),
                    color    = color(0.4, 0.7, 0.4),
                    anchored = true,
                })
                floor.name = "floor"
                """);

        Part floor = (Part) world.child("floor");
        assertEquals(new Vec3(60, 1, 60), floor.size);
        assertEquals(new Color(0.4f, 0.7f, 0.4f, 1.0f), floor.color);
        assertEquals(Vec3.ZERO, floor.cframe.position());
    }

    @Test
    void addReturnsTheProxyItCreated() {
        vm.run("t", """
                p = game.world:add("Part", { size = vec3(2, 2, 2) })
                x = p.size.x
                """);
        assertEquals(2.0, vm.number("x"));
    }

    @Test
    void addWithNoPropertiesWorks() {
        vm.run("t", """
                game.world:add("Folder")
                """);
        assertEquals("Folder", world.children().getFirst().def().name());
    }

    @Test
    void anUnknownClassIsAnError() {
        assertThrows(RuntimeException.class, () -> vm.run("t", """
                game.world:add("Wobble")
                """));
    }

    @Test
    void anUnknownPropertyInAddIsAnError() {
        assertThrows(RuntimeException.class,
                () -> vm.run("t", """
                game.world:add("Part", { wobble = 1 })
                """));
    }

    @Test
    void destroyTakesTheInstanceOutOfTheTree() {
        Instance part = Instances.create(Classes.PART, world, "p");
        vm.run("t", "game.world.p:destroy()");
        assertFalse(part.isAlive());
        assertEquals(0, world.children().size());
    }
}
