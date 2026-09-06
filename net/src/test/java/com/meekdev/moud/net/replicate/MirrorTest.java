package com.meekdev.moud.net.replicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MirrorTest {

    private InstanceTree authority;
    private Instance world;
    private Recorder recorder;
    private Applier applier;

    @BeforeEach
    void setUp() {
        authority = new InstanceTree();
        world = Instances.createRoot(authority, Classes.SPATIAL, "World");
        recorder = new Recorder();
        applier = new Applier(Classes.registry());
    }

    private void sync() {
        List<Change> changes = new ArrayList<>();
        recorder.follow(authority, changes::add);
        recorder.drain(changes::add);
        for (Change change : changes) applier.apply(change);
    }

    @Test
    void aCreatedPartAppearsWithItsProperties() {
        Instances.create(Classes.PART, world, "floor", p -> {
            p.size = new Vec3(60, 1, 60);
            p.cframe = CFrame.at(0, 64, 0);
        });
        sync();

        Instance mirrored = applier.world().child("floor");
        assertNotNull(mirrored);
        assertEquals(new Vec3(60, 1, 60), ((Part) mirrored).size);
        assertEquals(new Vec3(0, 64, 0), ((Part) mirrored).cframe.position());
    }

    @Test
    void idsMatchOnBothSides() {
        Instance part = Instances.create(Classes.PART, world, "p");
        sync();
        assertNotNull(applier.tree().byId(part.id()));
    }

    @Test
    void aLaterWriteCrossesOnItsOwn() {
        Part part = Instances.create(Classes.PART, world, "p");
        sync();
        Instances.setObj(part, part.def().property("cframe"), CFrame.at(9, 0, 0));
        sync();
        Part mirrored = (Part) applier.tree().byId(part.id());
        assertEquals(9.0, mirrored.cframe.position().x());
    }

    @Test
    void aDestroyedPartLeavesTheMirror() {
        Instance part = Instances.create(Classes.PART, world, "p");
        sync();
        Instances.destroy(part);
        sync();
        assertNull(applier.tree().byId(part.id()));
    }

    @Test
    void aNewTreeResetsTheMirror() {
        Instances.create(Classes.PART, world, "p");
        sync();
        assertEquals(1, applier.world().children().size());

        authority = new InstanceTree();
        world = Instances.createRoot(authority, Classes.SPATIAL, "World");
        sync();
        assertEquals(0, applier.world().children().size(), "a reload starts the mirror over");
    }

    @Test
    void aFieldCrossesInOneDrain() {
        for (int n = 0; n < 5000; n++) Instances.create(Classes.PART, world, "p" + n);
        sync();
        assertEquals(5000, applier.world().children().size());
    }
}
