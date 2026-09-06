package com.meekdev.moud.script.sched;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerTest {

    private Vm vm;

    @BeforeEach
    void setUp() {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        vm = new Vm();
        vm.bind(world, Classes.registry());
    }

    @AfterEach
    void tearDown() {
        vm.close();
    }

    @Test
    void spawnRunsUpToTheFirstWait() {
        vm.run("t", """
                before = 0
                after = 0
                task.spawn(function()
                    before = 1
                    task.wait(1)
                    after = 1
                end)
                """);
        assertEquals(1.0, vm.number("before"), "the body runs immediately");
        assertEquals(0.0, vm.number("after"), "and stops at the wait");
    }

    @Test
    void waitResumesAfterTheTimePasses() {
        vm.run("t", """
                done = 0
                task.spawn(function()
                    task.wait(0.5)
                    done = 1
                end)
                """);
        vm.step(0.2);
        assertEquals(0.0, vm.number("done"));
        vm.step(0.4);
        assertEquals(1.0, vm.number("done"));
    }

    @Test
    void severalWaitsRunInSequence() {
        vm.run("t", """
                n = 0
                task.spawn(function()
                    for i = 1, 3 do
                        task.wait(0.1)
                        n += 1
                    end
                end)
                """);
        for (int i = 0; i < 3; i++) vm.step(0.11);
        assertEquals(3.0, vm.number("n"));
    }

    @Test
    void delayRunsLater() {
        vm.run("t", """
                fired = 0
                task.delay(0.3, function() fired = 1 end)
                """);
        vm.step(0.2);
        assertEquals(0.0, vm.number("fired"));
        vm.step(0.2);
        assertEquals(1.0, vm.number("fired"));
    }

    @Test
    void cancelStopsAPendingTask() {
        vm.run("t", """
                fired = 0
                handle = task.spawn(function()
                    task.wait(0.5)
                    fired = 1
                end)
                task.cancel(handle)
                """);
        vm.step(1.0);
        assertEquals(0.0, vm.number("fired"));
        assertEquals(0, vm.scheduler().sleepingCount());
    }

    @Test
    void aFinishedTaskIsNotHeldOnTo() {
        vm.run("t", "task.spawn(function() task.wait(0.1) end)");
        assertEquals(1, vm.scheduler().sleepingCount());
        vm.step(0.2);
        assertEquals(0, vm.scheduler().sleepingCount());
    }
}
