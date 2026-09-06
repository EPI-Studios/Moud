package com.meekdev.moud.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransformsTest {

    private InstanceTree tree;
    private Instance world;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-9, "x");
        assertEquals(expected.y(), actual.y(), 1e-9, "y");
        assertEquals(expected.z(), actual.z(), 1e-9, "z");
    }

    @Test
    void childFramesAreRelativeToTheParent() {
        Spatial group = Instances.create(Classes.SPATIAL, world, "group");
        group.cframe = new CFrame(new Vec3(10, 0, 0), Quat.euler(0, Math.PI / 2, 0));

        Part child = Instances.create(Classes.PART, group, "child");
        child.cframe = CFrame.at(0, 0, -4);

        // group faces -x after a quarter turn, so the child's forward offset lands there
        assertVec(new Vec3(6, 0, 0), Transforms.world(child).position());
    }

    @Test
    void movingTheParentMovesTheChildren() {
        Spatial group = Instances.create(Classes.SPATIAL, world, "group");
        Part child = Instances.create(Classes.PART, group, "child");
        child.cframe = CFrame.at(0, 2, 0);

        group.cframe = CFrame.at(5, 0, 0);

        assertVec(new Vec3(5, 2, 0), Transforms.world(child).position());
    }

    @Test
    void localForSolvesBackToAWorldFrame() {
        Spatial group = Instances.create(Classes.SPATIAL, world, "group");
        group.cframe = new CFrame(new Vec3(3, 1, -2), Quat.euler(0.4, 1.2, 0));
        Part child = Instances.create(Classes.PART, group, "child");

        CFrame target = new CFrame(new Vec3(-7, 20, 3), Quat.euler(0, 0.9, 0.2));
        child.cframe = Transforms.localFor(child, target);

        assertVec(target.position(), Transforms.world(child).position());
    }

    @Test
    void movingOnePartInABigSceneCostsOneDirtyEntry() {
        Instance grid = Instances.create(Classes.FOLDER, world, "grid");
        Part[] parts = new Part[1024];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = Instances.create(Classes.PART, grid, "p" + i);
        }
        tree.drainDirty((i, mask) -> { });
        assertEquals(0, tree.dirtyCount());

        PropertyDef cframe = Classes.SPATIAL.property("cframe");
        Instances.setObj(parts[512], cframe, CFrame.at(1, 2, 3));

        assertEquals(1, tree.dirtyCount(), "a static scene should cost nothing to leave alone");
        assertEquals(1024, tree.ofClass(Classes.PART).size());
    }
}
