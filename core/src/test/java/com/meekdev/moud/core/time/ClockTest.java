package com.meekdev.moud.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClockTest {

    @Test
    void measuresTheGapInSeconds() {
        long[] now = {0};
        Clock clock = new Clock(() -> now[0]);
        now[0] = 16_000_000L;
        assertEquals(0.016, clock.tick(), 1e-9);
        now[0] = 32_000_000L;
        assertEquals(0.016, clock.tick(), 1e-9);
    }

    @Test
    void aLongStallIsClamped() {
        long[] now = {0};
        Clock clock = new Clock(() -> now[0]);
        now[0] = 30_000_000_000L;
        assertEquals(Clock.MAX_STEP, clock.tick());
    }

    @Test
    void timeNeverRunsBackwards() {
        long[] now = {100};
        Clock clock = new Clock(() -> now[0]);
        now[0] = 50;
        assertEquals(0.0, clock.tick());
    }
}
