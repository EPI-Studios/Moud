package com.meekdev.moud.script.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.script.vm.Vm;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

// the example place is a test artefact: if it does not run, the dev client shows nothing
class ExampleTest {

    private static final Path MAIN =
            Path.of(System.getProperty("user.home"), "Desktop/DEV/Moud-run/place/server/main.luau");

    @Test
    void theExamplePlaceRunsAndSteps() throws Exception {
        assumeTrue(Files.isRegularFile(MAIN), "no example place checked out");

        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        try (Vm vm = new Vm()) {
            vm.bind(world, Classes.registry());
            vm.run("main.luau", Files.readString(MAIN));
            assertFalse(world.children().isEmpty(), "the place built a scene");

            Part plate = (Part) world.children().get(0);
            CFrame before = plate.cframe;
            for (int n = 0; n < 40; n++) {
                vm.step(0.05);
                vm.renderStep(0.016);
            }
            assertNotEquals(before, plate.cframe, "stepping moved the platform");
        }
    }
}
