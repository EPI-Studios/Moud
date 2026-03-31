package com.moud.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ReleaseKeys {
    public static final String DEFAULT_PUBLIC_KEY_RESOURCE = "/moud/update/release-public-key.pem";

    private ReleaseKeys() {
    }

    public static String loadDefaultPublicKeyPem() {
        try (InputStream in = ReleaseKeys.class.getResourceAsStream(DEFAULT_PUBLIC_KEY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing release public key resource: " + DEFAULT_PUBLIC_KEY_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read release public key resource: " + DEFAULT_PUBLIC_KEY_RESOURCE, e);
        }
    }
}
