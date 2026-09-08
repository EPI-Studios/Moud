package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class InstanceSignalsTest {

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
    void changedNamesThePropertyThatWasWritten() {
        vm.run("main", """
                last = ""
                writes = 0
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.changed:connect(function(property)
                    last = property
                    writes += 1
                end)
                part.size = vec3(2, 2, 2)
                part.transparency = 0.5
                """);
        assertEquals("transparency", vm.text("last"));
        assertEquals(2, vm.number("writes"), 0);
    }

    @Test
    void aWriteThatChangesNothingIsNotAChange() {
        vm.run("main", """
                writes = 0
                local part = game.world:add("Part", { size = vec3(2, 2, 2) })
                part.changed:connect(function() writes += 1 end)
                part.size = vec3(2, 2, 2)
                """);
        assertEquals(0, vm.number("writes"), 0);
    }

    @Test
    void changedFiresForAWriteFromJava() {
        vm.run("main", """
                last = ""
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.name = "target"
                part.changed:connect(function(property) last = property end)
                """);
        Part part = (Part) world.child("target");
        Instances.setObj(part, part.def().property("size"), new Vec3(4, 4, 4));
        assertEquals("size", vm.text("last"));
    }

    @Test
    void childAddedCarriesTheChild() {
        vm.run("main", """
                seen = ""
                count = 0
                local folder = game.world:add("Folder")
                folder.childAdded:connect(function(child)
                    seen = child.name
                    count += 1
                end)
                folder:add("Part", { size = vec3(1, 1, 1) }).name = "first"
                local second = folder:add("Part", { size = vec3(1, 1, 1) })
                second.name = "second"
                """);
        assertEquals(2, vm.number("count"), 0);
        // the name is read when the signal fires, which is before the place has renamed it
        assertEquals("Part", vm.text("seen"));
    }

    @Test
    void childAddedFiresOncePerBulkEntry() {
        vm.run("main", """
                count = 0
                local folder = game.world:add("Folder")
                folder.childAdded:connect(function() count += 1 end)
                folder:addAll("Part", {
                    { size = vec3(1, 1, 1) },
                    { size = vec3(1, 1, 1) },
                    { size = vec3(1, 1, 1) },
                })
                """);
        assertEquals(3, vm.number("count"), 0);
    }

    @Test
    void destroyingRunsBeforeTheInstanceGoes() {
        vm.run("main", """
                seen = ""
                alive = false
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.name = "doomed"
                part.destroying:connect(function(dying)
                    seen = dying.name
                    alive = dying.parent ~= nil
                end)
                part:destroy()
                """);
        assertEquals("doomed", vm.text("seen"));
        assertEquals(true, vm.bool("alive"));
    }

    @Test
    void destroyingReachesChildrenDepthFirst() {
        vm.run("main", """
                order = ""
                local folder = game.world:add("Folder")
                folder.name = "group"
                local part = folder:add("Part", { size = vec3(1, 1, 1) })
                part.name = "leaf"
                folder.destroying:connect(function() order ..= "group " end)
                part.destroying:connect(function() order ..= "leaf " end)
                folder:destroy()
                """);
        assertEquals("leaf group ", vm.text("order"));
    }

    @Test
    void aDisconnectedHandlerStopsRunning() {
        vm.run("main", """
                writes = 0
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                local link = part.changed:connect(function() writes += 1 end)
                part.size = vec3(2, 2, 2)
                link:disconnect()
                part.size = vec3(3, 3, 3)
                """);
        assertEquals(1, vm.number("writes"), 0);
    }

    @Test
    void twoConnectionsToOneSignalBothRun() {
        vm.run("main", """
                a = 0
                b = 0
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.changed:connect(function() a += 1 end)
                part.changed:connect(function() b += 1 end)
                part.size = vec3(2, 2, 2)
                """);
        assertEquals(1, vm.number("a"), 0);
        assertEquals(1, vm.number("b"), 0);
    }
}
