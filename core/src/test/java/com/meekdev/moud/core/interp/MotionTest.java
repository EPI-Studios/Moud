package com.meekdev.moud.core.interp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MotionTest {

    private InstanceTree tree;
    private Instance world;
    private Motion motion;

    @BeforeEach
    void setUp() {
        tree = new InstanceTree();
        world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        motion = new Motion();
    }

    private void move(Instance i, double x) {
        Instances.setObj(i, i.def().property("cframe"), CFrame.at(x, 0, 0));
    }

    @Test
    void aTickWrittenPartSmoothsAcrossTheFramesAfterIt() {
        Part part = Instances.create(Classes.PART, world, "p");
        motion.drain(tree, 0.0);

        move(part, 10);
        motion.drain(tree, 0.05);
        assertEquals(0.0, motion.sample(part).position().x(), 1e-9, "the leg has only just started");

        motion.drain(tree, 0.025);
        assertEquals(5.0, motion.sample(part).position().x(), 1e-9, "half a tick in, half way");

        motion.drain(tree, 0.025);
        assertEquals(10.0, motion.sample(part).position().x(), 1e-9);
    }

    @Test
    void aStaticPartIsTrackedFromTheMomentItAppears() {
        Part part = Instances.create(Classes.PART, world, "p");
        move(part, 3);
        motion.drain(tree, 0.0);
        // the sample is the stored value, not a fresh walk of the parent chain
        assertSame(motion.sample(part), motion.sample(part));
    }

    @Test
    void aMovedParentCarriesItsChildren() {
        Instance folder = Instances.create(Classes.SPATIAL, world, "folder");
        Part child = Instances.create(Classes.PART, folder, "child");
        motion.drain(tree, 0.0);

        move(folder, 100);
        motion.drain(tree, 0.05);
        motion.drain(tree, 0.05);
        assertEquals(100.0, motion.sample(child).position().x(), 1e-9,
                "only the parent was dirty, but the child's world frame moved with it");
    }

    @Test
    void anUntrackedInstanceFallsBackToItsLiveFrame() {
        Part part = Instances.create(Classes.PART, world, "p");
        move(part, 7);
        assertEquals(7.0, motion.sample(part).position().x(), 1e-9);
    }
}
