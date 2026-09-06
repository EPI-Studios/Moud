package com.meekdev.moud.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.PropertyDef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceTreeTest {

    private InstanceTree tree;
    private Instance world;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, TestClasses.THING, "World");
    }

    @Test
    void childrenAreFoundByNameAndClass() {
        Instance a = Instances.create(TestClasses.GADGET, world, "floor");
        assertSame(a, world.child("floor"));
        assertEquals(List.of(a), tree.ofClass(TestClasses.GADGET));
        assertSame(a, tree.byId(a.id()));
    }

    @Test
    void destroyTakesTheSubtreeAndBumpsGeneration() {
        Instance group = Instances.create(TestClasses.THING, world, "group");
        Instance child = Instances.create(TestClasses.GADGET, group, "child");
        short before = child.generation();

        Instances.destroy(group);

        assertFalse(group.isAlive());
        assertFalse(child.isAlive());
        assertNull(tree.byId(child.id()));
        assertEquals(before + 1, child.generation());
        assertTrue(tree.ofClass(TestClasses.GADGET).isEmpty());
    }

    @Test
    void destroyFiresDeepestFirst() {
        Instance group = Instances.create(TestClasses.THING, world, "group");
        Instance child = Instances.create(TestClasses.GADGET, group, "child");
        List<String> order = new ArrayList<>();
        group.destroying().connect(i -> order.add("group"));
        child.destroying().connect(i -> order.add("child"));

        Instances.destroy(group);

        assertEquals(List.of("child", "group"), order);
    }

    @Test
    void reparentingUnderOwnDescendantIsRejected() {
        Instance group = Instances.create(TestClasses.THING, world, "group");
        Instance child = Instances.create(TestClasses.THING, group, "child");
        assertThrows(IllegalArgumentException.class, () -> Instances.reparent(group, child));
        assertThrows(IllegalArgumentException.class, () -> Instances.reparent(group, group));
    }

    @Test
    void writingMarksDirtyOnceAndDrainClears() {
        Instance part = Instances.create(TestClasses.GADGET, world, "p");
        PropertyDef charge = TestClasses.GADGET.property("charge");
        PropertyDef enabled = TestClasses.GADGET.property("enabled");

        Instances.setNum(part, charge, 0.5);
        Instances.setBool(part, enabled, false);

        assertEquals(1, tree.dirtyCount(), "one instance, not one per property");
        long expected = (1L << charge.index()) | (1L << enabled.index());
        assertEquals(expected, part.dirtyMask());

        List<Long> drained = new ArrayList<>();
        tree.drainDirty((i, mask) -> drained.add(mask));
        assertEquals(List.of(expected), drained);
        assertEquals(0, part.dirtyMask());
        assertEquals(0, tree.dirtyCount());
    }

    @Test
    void movingOnePartInABigSceneCostsOneDirtyEntry() {
        PropertyDef charge = TestClasses.GADGET.property("charge");
        List<Instance> parts = new ArrayList<>();
        for (int n = 0; n < 1000; n++) {
            parts.add(Instances.create(TestClasses.GADGET, world, "p" + n));
        }
        assertEquals(1000, tree.ofClass(TestClasses.GADGET).size());
        assertEquals(0, tree.dirtyCount(), "building a scene is a structure change, not a dirty one");

        Instance moved = parts.get(500);
        Instances.setNum(moved, charge, 0.25);
        assertEquals(1, tree.dirtyCount());

        List<Instance> drained = new ArrayList<>();
        tree.drainDirty((i, mask) -> drained.add(i));
        assertEquals(List.of(moved), drained);
        assertEquals(0, tree.dirtyCount());
    }

    @Test
    void writingTheSameValueDoesNothing() {
        Instance part = Instances.create(TestClasses.GADGET, world, "p");
        PropertyDef enabled = TestClasses.GADGET.property("enabled");
        Instances.setBool(part, enabled, true);
        assertEquals(0, part.dirtyMask());
        assertEquals(0, tree.dirtyCount());
    }

    @Test
    void numbersClampToTheirDeclaredRange() {
        Instance part = Instances.create(TestClasses.GADGET, world, "p");
        PropertyDef charge = TestClasses.GADGET.property("charge");
        Instances.setNum(part, charge, 5.0);
        assertEquals(1.0, charge.getNum(part));
    }

    @Test
    void inheritedPropertiesKeepTheirIndex() {
        PropertyDef onParent = TestClasses.THING.property("enabled");
        PropertyDef onChild = TestClasses.GADGET.property("enabled");
        assertEquals(onParent.index(), onChild.index());
        assertTrue(TestClasses.GADGET.isA(TestClasses.THING));
        assertFalse(TestClasses.THING.isA(TestClasses.GADGET));
    }

    @Test
    void theWrongSetterIsAnError() {
        Instance part = Instances.create(TestClasses.GADGET, world, "p");
        PropertyDef enabled = TestClasses.GADGET.property("enabled");
        assertThrows(IllegalArgumentException.class, () -> Instances.setNum(part, enabled, 1.0));
    }

    @Test
    void aPropertyFromAnotherClassIsAnError() {
        Instance thing = Instances.create(TestClasses.THING, world, "s");
        PropertyDef charge = TestClasses.GADGET.property("charge");
        assertThrows(IllegalArgumentException.class, () -> Instances.setNum(thing, charge, 0.5));
    }

    @Test
    void localInstancesGetNegativeIds() {
        Instance local = Instances.createLocal(TestClasses.GADGET, world, "hud");
        assertTrue(local.id() < 0);
        assertSame(local, tree.byId(local.id()));
    }

    @Test
    void writingToADestroyedInstanceThrows() {
        Instance part = Instances.create(TestClasses.GADGET, world, "p");
        PropertyDef charge = TestClasses.GADGET.property("charge");
        Instances.destroy(part);
        assertThrows(IllegalStateException.class, () -> Instances.setNum(part, charge, 0.5));
    }
}
