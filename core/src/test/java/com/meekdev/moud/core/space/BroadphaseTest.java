package com.meekdev.moud.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BroadphaseTest {

    private InstanceTree tree;
    private Instance world;
    private Broadphase grid;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        grid = new Broadphase(4.0);
    }

    private Part at(double x, double y, double z) {
        return Instances.create(Classes.PART, world, "p", p -> {
            p.cframe = CFrame.at(x, y, z);
            p.size = new Vec3(2, 2, 2);
        });
    }

    private static Aabb boxOf(Instance i) {
        Part p = (Part) i;
        return Aabb.around(p.cframe.position(), p.size);
    }

    private List<Instance> hits(Aabb region) {
        List<Instance> found = new ArrayList<>();
        grid.query(region, found::add);
        return found;
    }

    @Test
    void findsOnlyWhatOverlaps() {
        Part near = at(0, 0, 0);
        at(100, 0, 0);
        grid.rebuild(tree.ofClass(Classes.PART), BroadphaseTest::boxOf);

        List<Instance> found = hits(new Aabb(-1, -1, -1, 1, 1, 1));
        assertEquals(List.of(near), found);
    }

    @Test
    void spanningSeveralCellsDoesNotReportTwice() {
        Part wide = Instances.create(Classes.PART, world, "wide", p -> {
            p.cframe = CFrame.at(0, 0, 0);
            p.size = new Vec3(20, 2, 20);
        });
        grid.rebuild(tree.ofClass(Classes.PART), BroadphaseTest::boxOf);
        assertEquals(1, hits(new Aabb(-10, -1, -10, 10, 1, 10)).stream().distinct().count());
        assertTrue(hits(new Aabb(-10, -1, -10, 10, 1, 10)).contains(wide));
    }

    @Test
    void aMovedPartIsFoundAtItsNewPlaceAndNotItsOld() {
        Part part = at(0, 0, 0);
        grid.rebuild(tree.ofClass(Classes.PART), BroadphaseTest::boxOf);

        Instances.setObj(part, part.def().property("cframe"), CFrame.at(50, 0, 0));
        grid.put(part, boxOf(part));

        assertEquals(List.of(), hits(new Aabb(-1, -1, -1, 1, 1, 1)));
        assertEquals(List.of(part), hits(new Aabb(49, -1, -1, 51, 1, 1)));
    }

    @Test
    void aFieldOfPartsStaysCheapToQuery() {
        for (int x = 0; x < 150; x++) {
            for (int z = 0; z < 150; z++) at(x * 2, 0, z * 2);
        }
        grid.rebuild(tree.ofClass(Classes.PART), BroadphaseTest::boxOf);
        assertEquals(22500, grid.size());
        // a player sized query touches a handful, not the whole field
        assertTrue(hits(new Aabb(-0.5, -1, -0.5, 0.5, 1, 0.5)).size() <= 4);
    }

    // the region bkun hands the provider when it bakes static colliders. walking it cell by cell
    // is a hundred million billion lookups, which reads as a server that stops ticking
    @Test
    @Timeout(5)
    void theWholeWorldCostsWhatIsInItNotWhatItSpans() {
        for (int x = 0; x < 40; x++) at(x * 2, 0, 0);
        grid.rebuild(tree.ofClass(Classes.PART), BroadphaseTest::boxOf);

        assertEquals(40, hits(new Aabb(-3.0e7, -1024, -3.0e7, 3.0e7, 1024, 3.0e7)).size());
    }
}
