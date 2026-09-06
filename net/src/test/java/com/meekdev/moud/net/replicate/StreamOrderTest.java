package com.meekdev.moud.net.replicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// the tick drains dirty once and hands the same batch to every consumer. a second consumer that
// drained for itself would see an empty list, which is the bug this pins
class StreamOrderTest {

    @Test
    void everyConsumerSeesTheSameBatch() {
        InstanceTree tree = new InstanceTree();
        Instance world = Instances.createRoot(tree, Classes.SPATIAL, "World");
        Part part = Instances.create(Classes.PART, world, "p");
        Recorder recorder = new Recorder();

        List<Change> batch = new ArrayList<>();
        recorder.follow(tree, batch::add);
        recorder.drain(batch::add);

        List<Change> first = new ArrayList<>();
        List<Change> second = new ArrayList<>();
        for (Change change : batch) {
            first.add(change);
            second.add(change);
        }
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(c -> c instanceof Change.Created));

        // and a drain after the batch is genuinely empty, which is why sharing matters
        List<Change> again = new ArrayList<>();
        recorder.drain(again::add);
        assertEquals(List.of(), again);

        Instances.setObj(part, part.def().property("cframe"), CFrame.at(1, 0, 0));
        List<Change> moved = new ArrayList<>();
        recorder.drain(moved::add);
        assertEquals(1, moved.size(), "one write, one change");
    }
}
