package com.meekdev.moud.script.bench;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.Test;

// not an assertion, a measurement. core is microseconds and the binding is not, and the gap is
// the argument for a bulk creation api rather than a faster per call path
class BuildBench {

    private static final int N = 20000;

    @Test
    void coreIsNotTheBottleneck() {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        Instance grid = Instances.create(Classes.FOLDER, world, "grid");
        long t0 = System.nanoTime();
        for (int n = 0; n < N; n++) {
            Instances.create(Classes.PART, grid, "Part", p -> {
                p.size = new Vec3(1.6, 1.6, 1.6);
                p.cframe = CFrame.at(0, 64, 0);
                p.color = Color.WHITE;
            });
        }
        report("java create", System.nanoTime() - t0);
    }

    @Test
    void throughTheBinding() {
        time("luau vec3", "local v = vec3(1, 2, 3)");
        time("luau add", "grid:add(\"Part\", { size = vec3(1,1,1), position = vec3(0,64,0) })");
    }

    private void time(String what, String body) {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        try (Vm vm = new Vm()) {
            vm.bind(world, Classes.registry());
            long t0 = System.nanoTime();
            vm.run("bench", "local grid = game.world:add(\"Folder\")\nfor i = 1, " + N + " do\n" + body + "\nend");
            report(what, System.nanoTime() - t0);
        }
    }

    private static void report(String what, long nanos) {
        double ms = nanos / 1e6;
        System.out.printf("%-14s %6d  %8.0f ms  %7.1f us/op%n", what, N, ms, ms * 1000 / N);
    }
}
