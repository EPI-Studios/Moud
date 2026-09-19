package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.place.SyncedFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

record ClipFiles(String label, Map<String, String> files, boolean present, Disk disk) implements Edit {

    record Disk(Path root, BiConsumer<String, byte[]> written, BiConsumer<String, byte[]> removed, Consumer<String> note) {}

    @Override
    public void apply(SceneDocument document) {
        for (Map.Entry<String, String> file : files.entrySet()) {
            byte[] bytes = file.getValue().getBytes(StandardCharsets.UTF_8);
            if (present) put(file.getKey(), bytes);
            else take(file.getKey(), bytes);
        }
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new ClipFiles(label, files, !present, disk);
    }

    private void put(String res, byte[] bytes) {
        try {
            Path target = SyncedFiles.file(disk.root(), res);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                    disk.note().accept(Res.parse(res) + " is already there with other content and was kept");
                }
                return;
            }
            SyncedFiles.write(disk.root(), res, bytes);
        } catch (IOException | RuntimeException e) {
            disk.note().accept("could not write " + Res.parse(res) + ": " + e.getMessage());
            return;
        }
        disk.written().accept(res, bytes);
    }

    private void take(String res, byte[] bytes) {
        try {
            Path target = SyncedFiles.file(disk.root(), res);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !Arrays.equals(Files.readAllBytes(target), bytes)) {
                disk.note().accept(Res.parse(res) + " was edited after it was written and was kept");
                return;
            }
            if (!SyncedFiles.remove(disk.root(), res, bytes)) return;
        } catch (IOException | RuntimeException e) {
            disk.note().accept("could not remove " + Res.parse(res) + ": " + e.getMessage());
            return;
        }
        disk.removed().accept(res, bytes);
    }
}
