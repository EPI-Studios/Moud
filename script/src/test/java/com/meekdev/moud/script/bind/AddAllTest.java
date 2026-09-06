package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddAllTest {

    private Instance world;
    private Vm vm;

    @BeforeEach
    void setUp() {
        InstanceTree tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        vm = new Vm();
        vm.bind(world, Classes.registry());
    }

    @AfterEach
    void tearDown() {
        vm.close();
    }

    @Test
    void buildsEveryEntryWithItsOwnProperties() {
        vm.run("t", """
                game.world:addAll("Part", {
                    { position = vec3(1, 0, 0), size = vec3(2, 2, 2) },
                    { position = vec3(2, 0, 0) },
                    { position = vec3(3, 0, 0) },
                })
                """);
        assertEquals(3, world.children().size());
        Part first = (Part) world.children().getFirst();
        assertEquals(new Vec3(1, 0, 0), first.cframe.position());
        assertEquals(new Vec3(2, 2, 2), first.size);
        assertEquals(new Vec3(3, 0, 0), ((Part) world.children().get(2)).cframe.position());
    }

    @Test
    void returnsHowManyItMade() {
        vm.run("t", """
                n = game.world:addAll("Part", { {}, {}, {}, {} })
                """);
        assertEquals(4.0, vm.number("n"));
    }

    @Test
    void anEmptyListIsFine() {
        vm.run("t", "n = game.world:addAll(\"Part\", {})");
        assertEquals(0.0, vm.number("n"));
        assertEquals(0, world.children().size());
    }

    @Test
    void anUnknownPropertyIsStillAnError() {
        assertThrows(RuntimeException.class,
                () -> vm.run("t", "game.world:addAll(\"Part\", { { wobble = 1 } })"));
    }

    @Test
    void aBigFieldIsOneCrossing() {
        vm.run("t", """
                local list = table.create(20000)
                for i = 1, 20000 do
                    list[i] = { position = vec3(i, 0, 0) }
                end
                n = game.world:addAll("Part", list)
                """);
        assertEquals(20000.0, vm.number("n"));
        assertEquals(20000, world.children().size());
    }
}
