package com.meekdev.moud.script.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.vm.Vm;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersistTest {

    private Vm fresh() {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        Vm vm = new Vm();
        vm.bind(world, Classes.registry());
        return vm;
    }

    @Test
    void scalarsCrossAReload() {
        Map<String, Object> carried;
        try (Vm before = fresh()) {
            before.run("t", """
                    game.persist.score = 42
                    game.persist.name = "obby"
                    """);
            carried = before.persist();
        }
        try (Vm after = fresh()) {
            after.persist(carried);
            after.run("t", """
                    s = game.persist.score
                    n = game.persist.name
                    """);
            assertEquals(42.0, after.number("s"));
            assertEquals("obby", after.text("n"));
        }
    }

    @Test
    void nestedTablesCrossAReload() {
        Map<String, Object> carried;
        try (Vm before = fresh()) {
            before.run("t", """
                    game.persist.best = { lap = 3, who = "meek" }
                    """);
            carried = before.persist();
        }
        try (Vm after = fresh()) {
            after.persist(carried);
            after.run("t", """
                    lap = game.persist.best.lap
                    who = game.persist.best.who
                    """);
            assertEquals(3.0, after.number("lap"));
            assertEquals("meek", after.text("who"));
        }
    }

    @Test
    void valueTypesCrossAReload() {
        Map<String, Object> carried;
        try (Vm before = fresh()) {
            before.run("t", """
                    game.persist.spawn = vec3(1, 2, 3)
                    """);
            carried = before.persist();
        }
        try (Vm after = fresh()) {
            after.persist(carried);
            after.run("t", """
                    y = game.persist.spawn.y
                    """);
            assertEquals(2.0, after.number("y"));
        }
    }

    @Test
    void aFunctionDoesNotCross() {
        try (Vm before = fresh()) {
            before.run("t", """
                    game.persist.fn = function() end
                    game.persist.n = 1
                    """);
            Map<String, Object> carried = before.persist();
            assertTrue(carried.containsKey("n"));
            assertFalse(carried.containsKey("fn"), "a closure belongs to the vm that made it");
        }
    }
}
