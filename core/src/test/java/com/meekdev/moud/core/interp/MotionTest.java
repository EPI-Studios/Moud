package com.meekdev.moud.core.interp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aTickWrittenPartIsDrawnAcrossTheTickAfterIt() {
        Part part = Instances.create(Classes.PART, world, "p");
        motion.drain(tree);

        move(part, 10);
        motion.drain(tree);
        assertEquals(0.0, motion.sample(part, 0.0).position().x(), 1e-9, "the leg has only just started");
        assertEquals(5.0, motion.sample(part, 0.5).position().x(), 1e-9, "half a tick in, half way");
        assertEquals(10.0, motion.sample(part, 1.0).position().x(), 1e-9);
    }

    // the frame rate decides how often a leg is sampled, never how far along it is
    @Test
    void theFrameRateDoesNotChangeHowFarAlongTheLegIs() {
        Part part = Instances.create(Classes.PART, world, "p");
        motion.drain(tree);
        move(part, 10);
        motion.drain(tree);

        assertEquals(2.5, motion.sample(part, 0.25).position().x(), 1e-9);
        assertEquals(2.5, motion.sample(part, 0.25).position().x(), 1e-9, "sampling again moves nothing");
        assertEquals(7.5, motion.sample(part, 0.75).position().x(), 1e-9);
    }

    // the leg that stalled: it used to finish early and sit at its end waiting for the next write
    @Test
    void aPartWrittenEveryTickNeverRunsOutOfLeg() {
        Part part = Instances.create(Classes.PART, world, "p");
        motion.drain(tree);
        for (int tick = 1; tick <= 5; tick++) {
            move(part, tick * 10);
            motion.drain(tree);
            assertEquals((tick - 1) * 10.0, motion.sample(part, 0.0).position().x(), 1e-9);
            assertEquals(tick * 10.0 - 5.0, motion.sample(part, 0.5).position().x(), 1e-9);
            assertEquals(tick * 10.0, motion.sample(part, 1.0).position().x(), 1e-9);
        }
    }

    @Test
    void aStaticPartIsTrackedFromTheMomentItAppears() {
        Part part = Instances.create(Classes.PART, world, "p");
        move(part, 3);
        motion.drain(tree);
        // the sample is the stored value, not a fresh walk of the parent chain
        assertSame(motion.sample(part), motion.sample(part));
    }

    @Test
    void aMovedParentCarriesItsChildren() {
        Instance folder = Instances.create(Classes.SPATIAL, world, "folder");
        Part child = Instances.create(Classes.PART, folder, "child");
        motion.drain(tree);

        move(folder, 100);
        motion.drain(tree);
        assertEquals(100.0, motion.sample(child).position().x(), 1e-9,
                "only the parent was dirty, but the child's world frame moved with it");
    }

    @Test
    void stillPartsLeaveTheMovingSetSoTheyCostNothing() {
        for (int n = 0; n < 50; n++) Instances.create(Classes.PART, world, "p" + n);
        motion.drain(tree);
        assertEquals(0, motion.moving().size(), "a part that appeared is still, not in flight");

        move(world.children().get(0), 5);
        motion.drain(tree);
        assertEquals(1, motion.moving().size(), "only the one that was written");

        // one tick with nothing written is all it takes to settle
        motion.drain(tree);
        assertEquals(0, motion.moving().size(), "nothing is moving, so nothing is repacked");
    }

    @Test
    void onlyWhatMovesStaysInFlight() {
        Part still = Instances.create(Classes.PART, world, "still");
        Part mover = Instances.create(Classes.PART, world, "mover");
        motion.drain(tree);
        assertEquals(0, motion.moving().size());

        move(mover, 5);
        motion.drain(tree);
        assertTrue(motion.isMoving(mover));
        assertFalse(motion.isMoving(still));
    }

    @Test
    void theStillSetOnlyReportsAChangeWhenItChanges() {
        Instances.create(Classes.PART, world, "p");
        motion.drain(tree);
        assertTrue(motion.takeStillChanged(), "a new part changed the still set");
        motion.drain(tree);
        assertFalse(motion.takeStillChanged(), "a quiet frame does not repack anything");
    }

    @Test
    void anUntrackedInstanceFallsBackToItsLiveFrame() {
        Part part = Instances.create(Classes.PART, world, "p");
        move(part, 7);
        assertEquals(7.0, motion.sample(part).position().x(), 1e-9);
    }
}
