package com.meekdev.moud.core.instance;

import java.util.List;

// what a tick is
//
// five passes over the handful of instances that take part in each, in Stage order. the ordering
// between stages is the engine's whole control flow and it lives here, once, instead of as a
// sequence of static calls copied into a server scene and a client scene that could drift apart
//
// nothing in here knows what a body is. it asks instances to do their part and they know
public final class Stages {

    private Stages() {}

    public static void step(InstanceTree tree, double dt) {
        for (Stage stage : Stage.ORDER) run(tree, stage, dt);
    }

    // one stage on its own, which is what a caller wants when the stages are split across two
    // clocks: the server drives and simulates on its tick, a client evaluates and composes on its
    // frame so a body is posed at the rate it is drawn
    public static void run(InstanceTree tree, Stage stage, double dt) {
        List<Instance> taking = tree.inStage(stage);
        int count = taking.size();
        if (count == 0) return;

        Instance[] snapshot = tree.snapshot(taking);
        for (int n = 0; n < count; n++) {
            Instance i = snapshot[n];
            // a stage before this one may have destroyed it, and a destroyed instance has no frame
            // to write and no tree to write it in
            if (!i.isAlive()) continue;
            switch (stage) {
                case SHAPE -> i.shape();
                case DRIVE -> i.drive(dt);
                case EVALUATE -> i.evaluate();
                case SIMULATE -> i.simulate(dt);
                case COMPOSE -> i.compose();
            }
        }
    }
}
