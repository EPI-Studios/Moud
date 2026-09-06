package com.meekdev.moud.script.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.vm.Vm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignalsTest {

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
    void steppedRunsHandlersWithDelta() {
        vm.run("main", """
                total = 0
                game.stepped:connect(function(dt) total += dt end)
                """);
        vm.step(0.5);
        vm.step(0.25);
        assertEquals(0.75, vm.number("total"), 1e-9);
    }

    @Test
    void theDesignSnippetMovesAPart() {
        vm.run("main", """
                local platform = game.world:add("Part", {
                    size = vec3(6, 1, 6), position = vec3(0, 4, 10), anchored = true,
                })
                platform.name = "platform"
                local t = 0
                game.stepped:connect(function(dt)
                    t += dt
                    platform.position = vec3(t * 8, 4, 10)
                end)
                """);
        vm.step(0.5);
        Part platform = (Part) world.child("platform");
        assertEquals(4.0, platform.cframe.position().x(), 1e-9);
    }

    @Test
    void aDisconnectedHandlerStopsRunning() {
        vm.run("main", """
                n = 0
                c = game.stepped:connect(function() n += 1 end)
                """);
        vm.step(0.1);
        vm.run("main", "c:disconnect()");
        vm.step(0.1);
        assertEquals(1.0, vm.number("n"));
    }

    @Test
    void oneFailingHandlerDoesNotStopTheRest() {
        List<ScriptError> errors = new ArrayList<>();
        vm.onError(errors::add);
        vm.run("main", """
                ran = 0
                game.stepped:connect(function() error("boom") end)
                game.stepped:connect(function() ran += 1 end)
                """);
        vm.step(0.1);
        assertEquals(1.0, vm.number("ran"));
        assertTrue(errors.size() == 1, "the failing handler reported once");
    }
}
