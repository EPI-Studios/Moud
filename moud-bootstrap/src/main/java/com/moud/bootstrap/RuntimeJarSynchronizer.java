package com.moud.bootstrap;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class RuntimeJarSynchronizer {

    enum Status {
        INSTALLED,
        UP_TO_DATE,
        FAILED
    }

    record SyncResult(Status status, Path sourceJar, Path targetJar, String message) {
    }

    SyncResult sync(Path sourceJar, Path targetJar) {
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            return new SyncResult(Status.FAILED, sourceJar, targetJar, "runtime source jar is missing");
        }
        if (targetJar == null) {
            return new SyncResult(Status.FAILED, sourceJar, targetJar, "runtime target jar is missing");
        }

        try {
            Files.createDirectories(targetJar.getParent());

            if (Files.isRegularFile(targetJar) && Files.mismatch(sourceJar, targetJar) == -1L) {
                return new SyncResult(Status.UP_TO_DATE, sourceJar, targetJar, null);
            }

            Path tempJar = targetJar.resolveSibling(targetJar.getFileName() + ".tmp");
            try {
                Files.copy(sourceJar, tempJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(tempJar, targetJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempJar);
            }

            return new SyncResult(Status.INSTALLED, sourceJar, targetJar, null);
        } catch (IOException e) {
            return new SyncResult(Status.FAILED, sourceJar, targetJar, e.getMessage());
        }
    }
}
