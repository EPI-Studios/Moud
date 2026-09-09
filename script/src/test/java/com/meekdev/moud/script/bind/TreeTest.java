package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.vm.Vm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// walking and moving the tree from luau, which a place cannot do through named lookup alone
class TreeTest {

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
    void childrenComeBackInOrderAndAsTheSameInstances() {
        vm.run("main", """
                local folder = game.world:add("Folder")
                for i = 1, 3 do
                    folder:add("Part", { size = vec3(1, 1, 1) }).name = "p" .. i
                end
                local kids = folder:children()
                count = #kids
                names = kids[1].name .. kids[2].name .. kids[3].name
                same = kids[1] == folder:find("p1")
                """);
        assertEquals(3, vm.number("count"), 0);
        assertEquals("p1p2p3", vm.text("names"));
        assertTrue(vm.bool("same"), "a proxy compares equal to another for the same instance");
    }

    @Test
    void childrenOfALeafIsAnEmptyTable() {
        vm.run("main", """
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                count = #part:children()
                """);
        assertEquals(0, vm.number("count"), 0);
    }

    @Test
    void findReturnsNilRatherThanErroringForAMissingChild() {
        vm.run("main", """
                missing = game.world:find("nothing") == nil
                """);
        assertTrue(vm.bool("missing"));
    }

    // reaching for a member that is not there is still an error, so a typo fails (8.3 rule 6)
    @Test
    void reachingForAMissingMemberIsStillAnError() {
        assertThrows(RuntimeException.class, () -> vm.run("main", """
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                return part.nothingLikeThis
                """));
    }

    @Test
    void isAFollowsTheClassChain() {
        vm.run("main", """
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                partIsPart = part:isA("Part")
                partIsSpatial = part:isA("Spatial")
                partIsFolder = part:isA("Folder")
                """);
        assertTrue(vm.bool("partIsPart"));
        assertTrue(vm.bool("partIsSpatial"));
        assertTrue(!vm.bool("partIsFolder"));
    }

    @Test
    void isAOnAnUnknownClassIsAnError() {
        assertThrows(RuntimeException.class, () -> vm.run("main", """
                game.world:add("Part", { size = vec3(1, 1, 1) }):isA("Nonsense")
                """));
    }

    @Test
    void assigningParentMovesTheInstance() {
        vm.run("main", """
                local from = game.world:add("Folder")
                from.name = "from"
                local to = game.world:add("Folder")
                to.name = "to"
                local part = from:add("Part", { size = vec3(1, 1, 1) })
                part.name = "mover"
                part.parent = to
                """);
        Instance from = world.child("from");
        Instance to = world.child("to");
        assertNotNull(to);
        assertNull(from.child("mover"), "it left the folder it was in");
        assertNotNull(to.child("mover"), "it arrived in the one it was assigned to");
        assertEquals(to, to.child("mover").parent());
    }

    @Test
    void assigningParentNilSaysToUseDestroy() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> vm.run("main", """
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.parent = nil
                """));
        assertTrue(String.valueOf(failure.getMessage()).contains("destroy"),
                "the error names the way to detach: " + failure.getMessage());
    }

    @Test
    void aParentThatWouldMakeACycleIsRefused() {
        assertThrows(RuntimeException.class, () -> vm.run("main", """
                local outer = game.world:add("Folder")
                local inner = outer:add("Folder")
                outer.parent = inner
                """));
    }

    // the move has to reach the replication stream, or a place that reparents from luau moves
    // something the client never learns about
    @Test
    void assigningParentIsSomethingTheTreeCanReplay() {
        vm.run("main", """
                local to = game.world:add("Folder")
                to.name = "to"
                local part = game.world:add("Part", { size = vec3(1, 1, 1) })
                part.name = "mover"
                part.parent = to
                """);
        List<Integer> moved = new ArrayList<>();
        world.tree().drainMoved(moved::add);
        assertEquals(1, moved.size(), "the reparent was recorded for the stream");
        assertEquals(world.child("to").child("mover").id(), moved.get(0).intValue());
    }
}
