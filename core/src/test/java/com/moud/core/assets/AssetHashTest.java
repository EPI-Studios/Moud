package com.moud.core.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public final class AssetHashTest {
    @Test
    void sha256ComputesStableHex() {
        AssetHash hash = AssetHash.sha256("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash.hex());
    }
}

