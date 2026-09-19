package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.asset.BbmodelImport;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Retarget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class BbmodelWriter implements ModelImport {

    private static final int PLAYER_LIKE = 3;

    private final SceneDocument document;
    private final Supplier<Vector3> spawn;

    BbmodelWriter(SceneDocument document, Supplier<Vector3> spawn) {
        this.document = document;
        this.spawn = spawn;
    }

    @Override
    public Summary inspect(Path file) throws IOException {
        return new BbmodelFiles().inspect(file);
    }

    @Override
    public Outcome run(Path file, Summary summary, Choices choices) {
        String res = AssetFiles.res(file);
        if (res == null) return new Outcome(false, file.getFileName() + " has to be inside the place to be imported", List.of(), List.of());
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Outcome(false, "could not read " + file.getFileName() + ": " + e.getMessage(), List.of(), List.of());
        }
        Instance holder = Instances.createRoot(new InstanceTree(), Classes.FOLDER, "Scratch");
        BbmodelImport.Imported imported = BbmodelImport.build(text, res, holder);
        Map<String, Object> retarget = retarget(choices.joints());
        long onBody = choices.joints().values().stream().filter(joint -> !joint.isEmpty()).distinct().count();
        List<Instance> animations = imported.model().child("animations") instanceof Instance folder ? List.copyOf(folder.children()) : List.of();
        List<String> produced = new ArrayList<>(imported.files().keySet());
        String folder = folder(choices.folder());
        List<Path> written = new ArrayList<>();
        List<String> notes = new ArrayList<>(imported.notes());
        for (int n = 0; n < produced.size(); n++) {
            String name = n < summary.animations().size() ? summary.animations().get(n).name() : null;
            Instance animation = n < animations.size() ? animations.get(n) : null;
            if (name == null || !choices.animations().contains(name)) {
                if (animation != null) Instances.destroy(animation);
                continue;
            }
            String from = produced.get(n);
            String to = folder + from.substring(from.lastIndexOf('/') + 1);
            try {
                Path target = AssetFiles.root().resolve(Res.parse(to));
                Files.createDirectories(target.getParent());
                Files.writeString(target, clip(imported.files().get(from), retarget, onBody), StandardCharsets.UTF_8);
                written.add(target);
                Animators.forget(to);
            } catch (IOException | RuntimeException e) {
                notes.add("could not write " + to + ": " + e.getMessage());
                continue;
            }
            if (animation != null && animation.isA(Classes.ANIMATION)) Instances.setObj(animation, Classes.ANIMATION.property("animationId"), to);
        }
        if (imported.model().child("animations") instanceof Instance empty && empty.children().isEmpty()) Instances.destroy(empty);
        if (choices.model()) {
            imported.model().cframe = CFrame.at(spawn.get());
            document.insertBuilt(List.of(imported.model()), "Import " + imported.model().name());
        }
        String what = written.size() + (written.size() == 1 ? " clip" : " clips");
        String message = (choices.model() ? "imported " + imported.model().name() + " and " + what : "wrote " + what)
                + (written.isEmpty() ? "" : " to " + folder.substring(Res.SCHEME.length()));
        return new Outcome(true, message, written, notes);
    }

    static String folder(String chosen) {
        String clean = chosen.strip().replace('\\', '/');
        if (clean.startsWith(Res.SCHEME)) clean = clean.substring(Res.SCHEME.length());
        while (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) clean = ClipLibrary.FOLDER;
        return Res.SCHEME + (clean.endsWith("/") ? clean : clean + "/");
    }

    static Map<String, Object> retarget(Map<String, String> chosen) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> group : chosen.entrySet()) {
            String guess = Retarget.joint(group.getKey());
            if (!group.getValue().equals(guess == null ? "" : guess)) changed.put(group.getKey(), group.getValue());
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    static String clip(String text, Map<String, Object> retarget, long onBody) {
        if (retarget.isEmpty()) return text;
        Map<String, Object> root = new LinkedHashMap<>((Map<String, Object>) Json.parse(text));
        root.put("retarget", retarget);
        if (onBody >= PLAYER_LIKE) root.put("rig", "player");
        return BbmodelImport.write(root);
    }
}
