package com.meekdev.moud.script.reload;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// the io thread only ever sets a flag, the owning thread decides when that becomes a reload,
// because design 8.6 says reload lands at a defined point and never mid script
public final class Watcher implements AutoCloseable {

    private final AtomicBoolean dirty = new AtomicBoolean();
    private final WatchService service;
    private final Thread thread;

    public Watcher(Path root) throws IOException {
        service = root.getFileSystem().newWatchService();
        register(root);

        thread = new Thread(this::watch, "moud-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean take() {
        return dirty.getAndSet(false);
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = service.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | RuntimeException e) {
                return;
            }
            if (key == null) continue;
            boolean touched = false;
            for (var event : key.pollEvents()) {
                // the definitions a place writes for its editor end in .luau too, and a client starting writes
                // them: the server reloaded every time somebody joined, and handed out new remotes the
                // client had already looked up the old ones of
                String file = String.valueOf(event.context());
                if (file.endsWith(".luau") && !file.endsWith(".d.luau")) touched = true;
                // a folder made after the watch started is not watched until it is registered, so a
                // new lib/ full of modules would never reload anything
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                        && key.watchable() instanceof Path parent
                        && event.context() instanceof Path name) {
                    touched |= watchNew(parent.resolve(name));
                }
            }
            key.reset();
            if (touched) dirty.set(true);
        }
    }

    private void register(Path top) throws IOException {
        Files.walkFileTree(top, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName() != null && dir.getFileName().toString().equals(".moud")) return FileVisitResult.SKIP_SUBTREE;
                dir.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean watchNew(Path created) {
        if (!Files.isDirectory(created)) return false;
        try {
            register(created);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        thread.interrupt();
        try {
            service.close();
        } catch (IOException ignored) {
            // closing a watch service we are done with has nothing useful to report
        }
    }
}
