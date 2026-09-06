package com.meekdev.moud.script.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VmTest {

    @Test
    void runsLuauAndReadsBackAGlobal() {
        try (Vm vm = new Vm()) {
            vm.run("test", "answer = 1 + 1");
            assertEquals(2.0, vm.number("answer"));
        }
    }

    @Test
    void theStandardLibraryIsThere() {
        try (Vm vm = new Vm()) {
            vm.run("test", "n = math.floor(3.7) + string.len('abcd') + #{1, 2, 3}");
            assertEquals(10.0, vm.number("n"));
        }
    }
}
