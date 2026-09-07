package com.meekdev.moud.script.bind;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.script.vm.Vm;
import org.junit.jupiter.api.Test;

// the ramps collide square, and the first link in that chain is whether luau's cframe.angles
// reaches the part at all
class RotationTest {

    @Test
    void anglesReachThePart() {
        InstanceTree tree = new InstanceTree();
        var world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        try (Vm vm = new Vm()) {
            vm.bind(world, Classes.registry());
            vm.run("ramp", """
                    game.world:add("Part", {
                        size = vec3(10, 1, 6),
                        cframe = cframe(-30, 68, 30) * cframe.angles(0, 0, 0.15),
                        collides = true,
                    })
                    """);
        }
        Part part = tree.ofClass(Classes.PART).getFirst();
        Quat rotation = part.cframe.rotation();
        System.out.printf("part rotation x=%.6f y=%.6f z=%.6f w=%.6f%n",
                rotation.x(), rotation.y(), rotation.z(), rotation.w());
        assertNotEquals(1.0, rotation.w(), 1.0e-6, "cframe.angles produced no rotation");
        assertTrue(Math.abs(rotation.z()) > 1.0e-4, "the tilt is not on z");
    }
}
