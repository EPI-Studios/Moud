package com.meekdev.moud.mod.client.editor.document;

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
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.transport.payload.ScenePastedPayload;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
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

    public void save() {
        SceneLink.save();
    }

    private Map<Instance, List<Instance>> groupByParent(List<InstanceRef> roots) {
        Map<Instance, List<Instance>> byParent = new java.util.LinkedHashMap<>();
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
