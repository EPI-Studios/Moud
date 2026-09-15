package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.core.script.LocalScript;
import com.meekdev.moud.core.script.Script;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.transport.payload.ScenePastedPayload;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public final class SceneDocument {

    private static final String SCENE_TEXT_START = "{";

    private final Selection selection = new Selection();
    private final History history = new History(this);
    private final Map<Integer, InstanceRef> refs = new HashMap<>();
    private final Map<Integer, Paste> waiting = new HashMap<>();
    private Supplier<Vector3> spawnPoint = () -> Vector3.ZERO;
    private @Nullable InstanceTree seen;

    public Selection selection() {
        return selection;
    }

    public History history() {
        return history;
    }

    public void spawnPoint(Supplier<Vector3> source) {
        spawnPoint = source;
    }

    public @Nullable InstanceTree tree() {
        return ClientScene.tree();
    }

    public @Nullable Instance world() {
        return ClientScene.world();
    }

    public InstanceRef ref(int id) {
        return refs.computeIfAbsent(id, InstanceRef::new);
    }

    public @Nullable Instance find(int id) {
        InstanceTree tree = tree();
        Instance world = world();
        if (tree == null || world == null || id == 0) return null;
        if (id == world.id()) return world;
        Instance found = tree.byId(id);
        return found != null && found.isAlive() ? found : null;
    }

    public @Nullable Instance find(InstanceRef ref) {
        return find(ref.id);
    }

    public @Nullable Instance primary() {
        OptionalInt id = selection.primary();
        return id.isPresent() ? find(id.getAsInt()) : null;
    }

    public boolean pickable(@Nullable Instance instance) {
        return editable(instance) && !(instance instanceof Part part && part.locked);
    }

    public void selectParent() {
        List<Integer> chosen = new ArrayList<>();
        for (int id : selection.all()) {
            Instance instance = find(id);
            if (instance != null && editable(instance.parent()) && !chosen.contains(instance.parent().id())) chosen.add(instance.parent().id());
        }
        if (!chosen.isEmpty()) selection.set(chosen);
    }

    public void selectChildren() {
        List<Integer> chosen = new ArrayList<>();
        for (int id : selection.all()) {
            Instance instance = find(id);
            if (instance == null) continue;
            for (Instance child : instance.children()) {
                if (editable(child)) chosen.add(child.id());
            }
        }
        if (!chosen.isEmpty()) selection.set(chosen);
    }

    public void selectAll() {
        Instance world = world();
        if (world == null) return;
        List<Integer> chosen = new ArrayList<>();
        for (Instance child : world.children()) {
            if (editable(child)) chosen.add(child.id());
        }
        selection.set(chosen);
    }

    public void selectWhere(Predicate<Instance> test) {
        Instance world = world();
        if (world == null) return;
        List<Integer> chosen = new ArrayList<>();
        gather(world, test, chosen);
        selection.set(chosen);
    }

    private void gather(Instance at, Predicate<Instance> test, List<Integer> into) {
        for (Instance child : at.children()) {
            if (editable(child) && test.test(child)) into.add(child.id());
            gather(child, test, into);
        }
    }

    public boolean editable(@Nullable Instance instance) {
        return instance != null && instance != world() && instance.isAlive() && Place.authored(instance);
    }

    public boolean dirty() {
        return SceneLink.dirty();
    }

    public void frame(boolean gestureHeld) {
        InstanceTree tree = tree();
        if (tree != seen) {
            seen = tree;
            selection.clear();
            history.clear();
            refs.clear();
            waiting.clear();
        }
        for (ScenePastedPayload pasted = SceneLink.takePasted(); pasted != null; pasted = SceneLink.takePasted()) landed(pasted);
        selection.keep(id -> find(id) != null);
        selection.record();
        history.settle(gestureHeld);
    }

    public Object read(InstanceRef target, int property) {
        Instance instance = require(target);
        PropertyDef def = instance.def().property(property);
        if (def == null) throw new IllegalStateException("no such property");
        return wire(instance, def);
    }

    public static @Nullable Object wire(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.type() == PropertyType.INT) return (int) property.getNum(instance);
        if (property.type() == PropertyType.NUM) return property.getNum(instance);
        if (property.type() == PropertyType.REF) return property.getObj(instance) instanceof Instance target ? target.id() : null;
        return property.getObj(instance);
    }

    void write(InstanceRef target, int property, @Nullable Object value) {
        editableOrThrow(target);
        apply(new Change.Wrote(target.id, property, value));
    }

    void reparent(InstanceRef target, InstanceRef parent) {
        editableOrThrow(target);
        Instance into = find(parent);
        if (into == null || into != world() && !editable(into)) throw new IllegalStateException("the new parent is not part of the scene");
        apply(new Change.Moved(target.id, parent.id));
    }

    void tag(InstanceRef target, String tag, boolean added) {
        editableOrThrow(target);
        if (tag.isBlank()) throw new IllegalStateException("a tag can not be empty");
        apply(new Change.Tagged(target.id, tag, added));
    }

    public void addValue(int parentId, String className, String name) {
        ClassDef<?> def = Addons.classes().find(className);
        if (def == null || find(parentId) == null) return;
        String text;
        try {
            InstanceTree scratch = new InstanceTree();
            Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
            Instance made = Instances.create(def, holder, name.isBlank() ? className : name.strip());
            text = snapshot(List.of(made));
        } catch (RuntimeException e) {
            SceneLink.local("Could not add " + className + ": " + e.getMessage());
            return;
        }
        history.execute(new Paste(text, ref(parentId), new ArrayList<>(), new ArrayList<>(), false, "Add " + name));
    }

    void rename(InstanceRef target, String name) {
        editableOrThrow(target);
        if (name.isBlank()) throw new IllegalStateException("a name can not be empty");
        apply(new Change.Renamed(target.id, name));
    }

    void destroy(List<InstanceRef> roots) {
        for (InstanceRef root : roots) {
            if (editable(find(root))) apply(new Change.Destroyed(root.id));
        }
    }

    void paste(Paste paste) {
        int token = SceneLink.paste(paste.text(), paste.parent().id);
        waiting.put(token, paste);
    }

    private void landed(ScenePastedPayload pasted) {
        Paste paste = waiting.remove(pasted.token());
        if (paste == null || pasted.roots().length == 0) return;
        if (paste.roots().isEmpty()) {
            for (int id : pasted.roots()) paste.roots().add(ref(id));
            for (int id : pasted.all()) paste.all().add(ref(id));
        } else {
            remap(paste.roots(), pasted.roots());
            remap(paste.all(), pasted.all());
        }
        if (paste.select()) {
            selection.clear();
            for (int id : pasted.roots()) selection.add(id);
        }
    }

    private void remap(List<InstanceRef> into, int[] ids) {
        for (int n = 0; n < Math.min(into.size(), ids.length); n++) {
            InstanceRef ref = into.get(n);
            refs.remove(ref.id);
            ref.id = ids[n];
            refs.put(ids[n], ref);
        }
    }

    void collect(Instance instance, List<InstanceRef> into) {
        into.add(ref(instance.id()));
        for (Instance child : instance.children()) collect(child, into);
    }

    String snapshot(List<Instance> roots) {
        return Scene.save(roots);
    }

    public List<InstanceRef> selectedRoots() {
        Set<Integer> chosen = new HashSet<>(selection.all());
        List<InstanceRef> roots = new ArrayList<>();
        for (int id : selection.all()) {
            Instance instance = find(id);
            if (!editable(instance)) continue;
            boolean nested = false;
            for (Instance at = instance.parent(); at != null; at = at.parent()) {
                if (chosen.contains(at.id())) nested = true;
            }
            if (!nested) roots.add(ref(id));
        }
        return roots;
    }

    public void deleteSelected() {
        List<InstanceRef> roots = selectedRoots();
        if (roots.isEmpty()) return;
        history.execute(new Destroy(roots, roots.size() == 1 ? "Delete" : "Delete " + roots.size()));
        selection.clear();
    }

    public void duplicateSelected() {
        Map<Instance, List<Instance>> byParent = groupByParent(selectedRoots());
        List<Edit> pastes = new ArrayList<>();
        for (Map.Entry<Instance, List<Instance>> group : byParent.entrySet()) {
            pastes.add(Paste.fresh(snapshot(group.getValue()), ref(group.getKey().id()), "Duplicate"));
        }
        if (pastes.isEmpty()) return;
        history.execute(pastes.size() == 1 ? pastes.getFirst() : new Batch("Duplicate", pastes));
    }

    public @Nullable String copySelected() {
        List<Instance> roots = new ArrayList<>();
        for (InstanceRef root : selectedRoots()) roots.add(find(root));
        return roots.isEmpty() ? null : snapshot(roots);
    }

    public void pasteText(@Nullable String text) {
        if (text == null || !text.stripLeading().startsWith(SCENE_TEXT_START) || !text.contains("\"instances\"")) {
            SceneLink.local("The clipboard does not hold scene instances");
            return;
        }
        Instance primary = primary();
        Instance parent = editable(primary) && primary.parent() != null ? primary.parent() : world();
        if (parent == null) return;
        history.execute(Paste.fresh(text, ref(parent.id()), "Paste"));
    }

    public void insert(String className, int parentId) {
        ClassDef<?> def = Addons.classes().find(className);
        Instance parent = find(parentId);
        if (def == null || parent == null) return;
        String text;
        try {
            InstanceTree scratch = new InstanceTree();
            Instance root = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
            Instance made = Instances.create(def, root, className);
            if (made instanceof Spatial spatial) {
                Vector3 at = spawnPoint.get();
                if (made instanceof Part part) at = at.add(new Vector3(0, part.size.y() * 0.5, 0));
                CFrame world = CFrame.at(at);
                spatial.cframe = parent instanceof Spatial ? Transforms.world(parent).inverse().mul(world) : world;
            }
            text = snapshot(List.of(made));
        } catch (RuntimeException e) {
            SceneLink.local("Could not insert " + className + ": " + e.getMessage());
            return;
        }
        history.execute(Paste.fresh(text, ref(parentId), "Insert " + className));
    }

    public void insertScript(ScriptTemplate template, boolean local, int parentId) {
        Instance parent = find(parentId);
        if (parent == null) return;
        String side = local ? "client" : "server";
        String text;
        try {
            Path folder = PlaceToml.root().resolve(side).resolve("scripts");
            Files.createDirectories(folder);
            String name = template.name();
            for (int n = 2; Files.exists(folder.resolve(name + ".luau")); n++) name = template.name() + n;
            Files.writeString(folder.resolve(name + ".luau"), template.code());
            InstanceTree scratch = new InstanceTree();
            Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
            if (local) {
                Instances.create(Classes.LOCAL_SCRIPT, holder, name).source = "res://" + side + "/scripts/" + name + ".luau";
            } else {
                Instances.create(Classes.SCRIPT, holder, name).source = "res://" + side + "/scripts/" + name + ".luau";
            }
            text = snapshot(new ArrayList<>(holder.children()));
        } catch (IOException | RuntimeException e) {
            SceneLink.local("Could not add a script: " + e.getMessage());
            return;
        }
        history.execute(Paste.fresh(text, ref(parentId), "Add " + template.label()));
    }

    public boolean placeAsset(String res, int parentId, @Nullable Vector3 at) {
        Instance parent = find(parentId);
        if (parent == null) return false;
        String file = res.substring(res.lastIndexOf('/') + 1);
        int dot = file.indexOf('.', 1);
        String name = dot < 0 ? file : file.substring(0, dot);
        String lower = file.toLowerCase(Locale.ROOT);
        String text;
        try {
            InstanceTree scratch = new InstanceTree();
            Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
            if (lower.endsWith(".scene")) {
                byte[] bytes = PlaceFiles.read(res);
                if (bytes == null) throw new IllegalStateException(res + " is not there");
                List<Instance> loaded = Scene.load(new String(bytes, StandardCharsets.UTF_8), holder, Addons.classes());
                if (at != null) shiftTo(loaded, parent, at);
                text = snapshot(loaded);
            } else {
                Instance made = madeFor(lower, res, name, holder);
                if (made == null) return false;
                if (made instanceof Spatial spatial) {
                    Vector3 point = at != null ? at : spawnPoint.get();
                    if (made instanceof Part part) point = point.add(new Vector3(0, part.size.y() * 0.5, 0));
                    spatial.cframe = Transforms.world(parent).inverse().mul(CFrame.at(point));
                }
                text = snapshot(List.of(made));
            }
        } catch (RuntimeException e) {
            SceneLink.local("Could not place " + file + ": " + e.getMessage());
            return false;
        }
        history.execute(Paste.fresh(text, ref(parentId), "Place " + name));
        return true;
    }

    private static @Nullable Instance madeFor(String lower, String res, String name, Instance holder) {
        if (lower.endsWith(".gltf") || lower.endsWith(".glb") || lower.endsWith(".bbmodel") || lower.endsWith(".ammesh")) {
            MeshPart mesh = Instances.create(Classes.MESH_PART, holder, name);
            mesh.meshId = res;
            return mesh;
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".flac")) {
            Sound sound = Instances.create(Classes.SOUND, holder, name);
            sound.soundId = res;
            return sound;
        }
        if (lower.endsWith(".luau") || lower.endsWith(".rv") || lower.endsWith(".java")) {
            if (res.startsWith(Res.SCHEME + "client/")) {
                LocalScript script = Instances.create(Classes.LOCAL_SCRIPT, holder, name);
                script.source = res;
                return script;
            }
            Script script = Instances.create(Classes.SCRIPT, holder, name);
            script.source = res;
            return script;
        }
        return null;
    }

    private static void shiftTo(List<Instance> roots, Instance parent, Vector3 at) {
        Vector3 anchor = null;
        for (Instance root : roots) {
            if (root instanceof Spatial spatial) {
                anchor = spatial.cframe.position();
                break;
            }
        }
        if (anchor == null) return;
        CFrame parentWorld = Transforms.world(parent);
        Vector3 offset = parentWorld.inverse().pointToWorld(at).sub(anchor);
        for (Instance root : roots) {
            if (root instanceof Spatial spatial) spatial.cframe = spatial.cframe.withPosition(spatial.cframe.position().add(offset));
        }
    }

    public void pasteTransformed(List<InstanceRef> roots, UnaryOperator<CFrame> move, String label, boolean select) {
        Map<Instance, List<Instance>> byParent = groupByParent(roots);
        List<Edit> pastes = new ArrayList<>();
        for (Map.Entry<Instance, List<Instance>> group : byParent.entrySet()) {
            String text;
            try {
                InstanceTree scratch = new InstanceTree();
                Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
                CFrame parentWorld = Transforms.world(group.getKey());
                for (Instance member : group.getValue()) {
                    Instance copy = Scene.load(snapshot(List.of(member)), holder, Addons.classes()).getFirst();
                    if (!(copy instanceof Spatial spatial)) continue;
                    CFrame local = parentWorld.inverse().mul(move.apply(Transforms.world(member)));
                    spatial.cframe = spatial.pivot.equals(Vector3.ZERO) ? local : local.mul(CFrame.at(spatial.pivot));
                }
                text = snapshot(new ArrayList<>(holder.children()));
            } catch (RuntimeException e) {
                SceneLink.local("Could not copy: " + e.getMessage());
                return;
            }
            pastes.add(new Paste(text, ref(group.getKey().id()), new ArrayList<>(), new ArrayList<>(), select, label));
        }
        if (pastes.isEmpty()) return;
        history.execute(pastes.size() == 1 ? pastes.getFirst() : new Batch(label, pastes));
    }

    public void group() {
        List<InstanceRef> roots = selectedRoots();
        List<Instance> members = new ArrayList<>();
        for (InstanceRef root : roots) members.add(find(root));
        if (members.isEmpty() || members.getFirst().parent() == null) return;
        Instance parent = members.getFirst().parent();
        String text;
        try {
            InstanceTree scratch = new InstanceTree();
            Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
            Instance folder = Instances.create(Classes.FOLDER, holder, "Group");
            relocate(members, parent, folder);
            text = snapshot(List.of(folder));
        } catch (RuntimeException e) {
            SceneLink.local("Could not group: " + e.getMessage());
            return;
        }
        selection.clear();
        history.execute(new Batch(roots.size() == 1 ? "Group" : "Group " + roots.size(), List.of(Paste.fresh(text, ref(parent.id()), "Group"), new Destroy(roots, "Group"))));
    }

    public void ungroup() {
        List<Edit> edits = new ArrayList<>();
        for (InstanceRef root : selectedRoots()) {
            Instance group = find(root);
            if (group == null || group.parent() == null || group.children().isEmpty()) continue;
            String text;
            try {
                InstanceTree scratch = new InstanceTree();
                Instance holder = Instances.createRoot(scratch, Classes.FOLDER, "Scratch");
                relocate(new ArrayList<>(group.children()), group.parent(), holder);
                text = snapshot(new ArrayList<>(holder.children()));
            } catch (RuntimeException e) {
                SceneLink.local("Could not ungroup " + group.name() + ": " + e.getMessage());
                continue;
            }
            edits.add(Paste.fresh(text, ref(group.parent().id()), "Ungroup"));
            edits.add(new Destroy(List.of(root), "Ungroup"));
        }
        if (edits.isEmpty()) return;
        selection.clear();
        history.execute(new Batch("Ungroup", edits));
    }

    private void relocate(List<Instance> members, Instance parent, Instance into) {
        CFrame parentWorld = Transforms.world(parent);
        for (Instance member : members) {
            Instance copy = Scene.load(snapshot(List.of(member)), into, Addons.classes()).getFirst();
            if (!(copy instanceof Spatial spatial)) continue;
            CFrame local = parentWorld.inverse().mul(Transforms.world(member));
            spatial.cframe = spatial.pivot.equals(Vector3.ZERO) ? local : local.mul(CFrame.at(spatial.pivot));
        }
    }

    public void save() {
        SceneLink.save();
    }

    Map<Instance, List<Instance>> groupByParent(List<InstanceRef> roots) {
        Map<Instance, List<Instance>> byParent = new LinkedHashMap<>();
        for (InstanceRef root : roots) {
            Instance instance = find(root);
            if (instance == null || instance.parent() == null) continue;
            byParent.computeIfAbsent(instance.parent(), key -> new ArrayList<>()).add(instance);
        }
        return byParent;
    }

    private void apply(Change change) {
        InstanceTree tree = tree();
        Instance world = world();
        if (tree == null || world == null) throw new IllegalStateException("there is no scene");
        byte[] bytes = Codec.encode(List.of(change), tree, Addons.classes());
        Applier applier = new Applier(Addons.classes(), tree, world);
        for (Change normalized : Codec.decode(bytes, tree, Addons.classes())) {
            applier.apply(normalized);
            if (normalized instanceof Change.Wrote wrote) {
                Instance instance = find(wrote.id());
                PropertyDef property = instance == null ? null : instance.def().property(wrote.property());
                if (property != null) PendingEdits.sent(wrote.id(), wrote.property(), wire(instance, property));
            }
        }
        SceneLink.send(bytes);
    }

    private Instance require(InstanceRef target) {
        Instance instance = find(target);
        if (instance == null) throw new IllegalStateException("that instance is gone");
        return instance;
    }

    private void editableOrThrow(InstanceRef target) {
        if (!editable(find(target))) throw new IllegalStateException("that instance is not part of the scene");
    }
}
