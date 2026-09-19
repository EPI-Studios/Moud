package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.asset.BbmodelImport;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.character.Animation;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Rigs;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Destroy;
import com.meekdev.moud.mod.client.editor.document.Paste;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class Rigging {

    private static final double SAME = 0.02;

    public record Made(Model model, Map<String, String> files, List<String> notes) {}

    private Rigging() {}

    public static boolean animatable(@Nullable Instance instance) {
        if (!(instance instanceof MeshPart mesh) || !mesh.meshId.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) return false;
        for (Instance child : mesh.children()) {
            if (child instanceof Bone) return false;
        }
        return true;
    }

    public static String directory(String name) {
        String clean = name.strip().replaceAll("[\\\\/:*?\"<>|]", "_");
        return ClipLibrary.FOLDER + "/" + (clean.isEmpty() || clean.chars().allMatch(c -> c == '.') ? "model" : clean) + "/";
    }

    public static String folder(String name) {
        return Res.SCHEME + directory(name);
    }

    public static @Nullable Made build(String text, String res, String name, Instance holder) {
        BbmodelImport.Imported imported = BbmodelImport.build(text, res, holder);
        Model model = imported.model();
        if (Rigs.jointsIn(model).isEmpty()) {
            Instances.destroy(model);
            return null;
        }
        if (!model.name().equals(name)) Instances.rename(model, name);
        String folder = folder(name);
        Map<String, String> files = new LinkedHashMap<>();
        if (model.child("animations") instanceof Instance animations) {
            for (Instance child : animations.children()) {
                if (!(child instanceof Animation animation) || !(imported.files().get(animation.animationId) instanceof String clip)) continue;
                String to = folder + child.name() + ClipLibrary.EXTENSION;
                files.put(to, rigged(clip, name));
                Instances.setObj(animation, Classes.ANIMATION.property("animationId"), to);
            }
        }
        return new Made(model, files, new ArrayList<>(imported.notes()));
    }

    public static @Nullable Made from(MeshPart mesh, String text, Instance holder) {
        Made made = build(text, mesh.meshId, mesh.name(), holder);
        if (made == null) return null;
        Model model = made.model();
        if (!(model.primaryPart instanceof MeshPart built)) throw new IllegalStateException("the importer made no mesh");
        double scale = scale(mesh.size, built.size, mesh.name(), made.notes());
        if (scale != 1) grow(model, scale);
        MeshPart copy = (MeshPart) Scene.paste(Scene.save(List.of(mesh)), model, Addons.classes()).getFirst();
        for (Instance child : List.copyOf(built.children())) Instances.reparent(child, copy);
        copy.cframe = built.cframe;
        copy.pivot = Vector3.ZERO;
        copy.size = built.size;
        if (!mesh.animation.isEmpty()) {
            made.notes().add(mesh.name() + " played " + mesh.animation + " by itself, a rigged model plays its clips from a script");
            copy.animation = "";
        }
        String name = built.name();
        Instances.destroy(built);
        Instances.rename(copy, name);
        Instances.setObj(model, Classes.MODEL.property("primaryPart"), copy);
        CFrame world = Transforms.world(mesh).mul(copy.cframe.inverse());
        model.cframe = mesh.parent() == null ? world : Transforms.world(mesh.parent()).inverse().mul(world);
        return made;
    }

    static double scale(Vector3 size, Vector3 natural, String name, List<String> notes) {
        double x = size.x() / natural.x();
        double y = size.y() / natural.y();
        double z = size.z() / natural.z();
        double even = (x + y + z) / 3;
        if (!(even > 0) || !Double.isFinite(even)) return 1;
        if (Math.max(x, Math.max(y, z)) - Math.min(x, Math.min(y, z)) > SAME * even) {
            notes.add(name + " was stretched unevenly, the rigged model keeps an even scale of " + ClipFile.number(Math.round(even * 1000) / 1000.0));
        }
        return Math.abs(even - 1) < SAME ? 1 : even;
    }

    public static void grow(Model model, double scale) {
        resize(model, scale);
        model.scale *= scale;
    }

    private static void resize(Instance at, double scale) {
        for (Instance child : at.children()) {
            if (child instanceof Spatial spatial) {
                spatial.cframe = spatial.cframe.withPosition(spatial.cframe.position().mul(scale));
                spatial.pivot = spatial.pivot.mul(scale);
            }
            if (child instanceof Part part) part.size = part.size.mul(scale);
            resize(child, scale);
        }
    }

    @SuppressWarnings("unchecked")
    static String rigged(String clip, String name) {
        if (!(Json.parse(clip) instanceof Map<?, ?> parsed)) return clip;
        Map<String, Object> root = new LinkedHashMap<>((Map<String, Object>) parsed);
        root.put("rig", name);
        return BbmodelImport.write(root);
    }

    public static List<Path> write(Path root, Map<String, String> files, Consumer<Path> upload, List<String> notes) {
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = root.resolve(Res.parse(file.getKey()));
            if (Files.exists(target)) {
                notes.add(Res.parse(file.getKey()) + " is already there and was kept");
                continue;
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                notes.add("could not write " + Res.parse(file.getKey()) + ": " + e.getMessage());
                continue;
            }
            written.add(target);
            Animators.forget(file.getKey());
            upload.accept(target);
        }
        return written;
    }

    public static Batch edit(SceneDocument document, MeshPart mesh, Model model) {
        Paste paste = Paste.fresh(Scene.save(List.of(model)), document.ref(mesh.parent().id()), label(mesh));
        return new Batch(label(mesh), List.of(paste, new Destroy(List.of(document.ref(mesh.id())), label(mesh))));
    }

    private static String label(MeshPart mesh) {
        return "Make " + mesh.name() + " animatable";
    }
}
