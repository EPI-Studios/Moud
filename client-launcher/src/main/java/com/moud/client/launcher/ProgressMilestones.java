package com.moud.client.launcher;

import java.util.concurrent.atomic.AtomicInteger;

final class ProgressMilestones {

    private final AtomicInteger lastLoggedMilestone = new AtomicInteger(-1);

    void log(long downloaded, long total) {
        if (total <= 0) {
            return;
        }
        int pct = (int) (downloaded * 100 / total);
        int milestone = Math.min(100, (pct / 25) * 25);
        if (milestone >= 0 && lastLoggedMilestone.getAndSet(milestone) != milestone) {
            ClientLauncher.log("Downloading... " + milestone + "%");
        }
    }
}
