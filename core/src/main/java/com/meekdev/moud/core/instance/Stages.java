package com.meekdev.moud.core.instance;

import java.util.List;

public final class Stages {

    private Stages() {}

    public static void step(InstanceTree tree, double dt) {
        for (Stage stage : Stage.ORDER) run(tree, stage, dt);
    }

    public static void run(InstanceTree tree, Stage stage, double dt) {
        List<Instance> taking = tree.inStage(stage);
        int count = taking.size();
        if (count == 0) return;

        Instance[] snapshot = tree.snapshot(taking);
        for (int n = 0; n < count; n++) {
            Instance i = snapshot[n];
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
