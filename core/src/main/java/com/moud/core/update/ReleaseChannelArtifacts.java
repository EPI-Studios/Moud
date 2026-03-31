package com.moud.core.update;

import java.util.List;
import java.util.Objects;

public record ReleaseChannelArtifacts(
        ReleaseArtifact full,
        List<ReleasePatchArtifact> patches
) {
    public ReleaseChannelArtifacts {
        patches = patches == null ? List.of() : List.copyOf(patches);
    }

    public void validate(String context) {
        String prefix = context == null || context.isBlank() ? "channel" : context;
        if (full == null) {
            throw new IllegalArgumentException(prefix + ": full artifact is required");
        }
        full.validate(prefix + ".full");
        for (int i = 0; i < patches.size(); i++) {
            ReleasePatchArtifact patch = Objects.requireNonNull(patches.get(i), prefix + ".patches[" + i + "]");
            patch.validate(prefix + ".patches[" + i + "]");
        }
    }
}
