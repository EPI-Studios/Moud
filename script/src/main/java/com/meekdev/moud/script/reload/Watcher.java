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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Watcher implements AutoCloseable {

    private final Set<Path> changed = new LinkedHashSet<>();
    private final WatchService service;
    private final Thread thread;
    private final Collection<String> extensions;

    public Watcher(Path root, Collection<String> extensions) throws IOException {
        this.extensions = extensions;
        service = root.getFileSystem().newWatchService();
        register(root);

        thread = new Thread(this::watch, "moud-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean take() {
        return !changes().isEmpty();
    }

    public Set<Path> changes() {
        synchronized (changed) {
            Set<Path> out = new LinkedHashSet<>(changed);
            changed.clear();
            return out;
        }
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
            Set<Path> touched = new LinkedHashSet<>();
            for (var event : key.pollEvents()) {
                if (!(key.watchable() instanceof Path parent) || !(event.context() instanceof Path name)) continue;
                Path path = parent.resolve(name);
                if (isScript(name.toString())) touched.add(path);
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && watchNew(path)) touched.add(path);
            }
            key.reset();
            if (!touched.isEmpty()) {
                synchronized (changed) {
                    changed.addAll(touched);
                }
            }
        }
    }

    private boolean isScript(String file) {
        for (String extension : extensions) {
            if (file.endsWith("." + extension) && !file.endsWith(".d." + extension)) return true;
        }
        return false;
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
        }
    }
}
