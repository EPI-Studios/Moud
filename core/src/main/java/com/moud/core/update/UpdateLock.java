package com.moud.core.update;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class UpdateLock implements Closeable {

    private final FileChannel channel;
    private final FileLock lock;
    private final Path lockFile;

    private UpdateLock(FileChannel channel, FileLock lock, Path lockFile) {
        this.channel = channel;
        this.lock = lock;
        this.lockFile = lockFile;
    }

    public static UpdateLock acquire(Path lockFile) throws IOException {
        UpdateLock result = tryAcquire(lockFile);
        if (result == null) {
            throw new IOException("Could not acquire update lock: " + lockFile
                    + " — another update may be in progress");
        }
        return result;
    }

    public static UpdateLock tryAcquire(Path lockFile) throws IOException {
        if (lockFile == null) throw new IllegalArgumentException("lockFile is null");
        Path parent = lockFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new UpdateLock(channel, lock, lockFile);
        } catch (Exception e) {
            channel.close();
            throw e;
        }
    }

    public boolean isHeld() {
        return lock != null && lock.isValid();
    }

    @Override
    public void close() throws IOException {
        try {
            if (lock != null && lock.isValid()) lock.release();
        } finally {
            channel.close();
        }
    }
}
