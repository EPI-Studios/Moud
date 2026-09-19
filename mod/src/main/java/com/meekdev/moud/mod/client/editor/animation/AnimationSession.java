package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class AnimationSession {

    public enum Tool { ROTATE, MOVE }

    private static final AnimClip NOTHING = new AnimClip();

    private final SceneDocument document;
    private final ClipLibrary library = new ClipLibrary();
    private @Nullable Path current;
    private double time;
    private boolean playing;
    private boolean looping = true;
    private double lastTime;
    private @Nullable String joint;
    private @Nullable Instance control;
    private @Nullable AnimClip compiledFrom;
    private Clip compiled = Clip.EMPTY;
    private final Set<KeyRef> keys = new LinkedHashSet<>();
    private int event = -1;
    private int marker = -1;
    private final Set<String> expanded = new LinkedHashSet<>();
    private List<KeyEdits.Copied> clipboard = List.of();
    private boolean snap = true;
    private boolean onion = true;
    private int onionBefore = 2;
    private int onionAfter = 2;
    private boolean local = true;
    private boolean ikDrag;
    private boolean bones;
    private Tool tool = Tool.ROTATE;
    private int gestures;
    private String message = "";
    private long messageAt;

    AnimationSession(SceneDocument document) {
        this.document = document;
    }

    ClipLibrary library() {
        return library;
    }

    public boolean hasClip() {
        return current != null && library.get(current) != null;
    }

    public AnimClip clip() {
        if (current == null) return NOTHING;
        ClipLibrary.Open loaded = library.get(current);
        return loaded == null ? NOTHING : loaded.clip;
    }

    public Clip compiled() {
        AnimClip now = clip();
        if (now != compiledFrom) {
            compiled = ClipFile.compile(now);
            compiledFrom = now;
        }
        return compiled;
    }

    public String channel(String joint, boolean retarget) {
        String channel = compiled().channelFor(joint, retarget);
        return channel == null ? joint : channel;
    }

    static String res(Path path) {
        return "res://" + AssetFiles.root().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    @Nullable AnimClip clipAt(Path path) {
        ClipLibrary.Open loaded = library.get(path);
        return loaded == null ? null : loaded.clip;
    }

    public @Nullable Path path() {
        return current;
    }

    public String name() {
        return current == null ? "" : ClipLibrary.name(current);
    }

    public boolean dirty() {
        if (current == null) return false;
        ClipLibrary.Open loaded = library.get(current);
        return loaded != null && loaded.dirty();
    }

    public boolean anyDirty() {
        return library.anyDirty();
    }

    public @Nullable String older() {
        if (current == null) return null;
        ClipLibrary.Open loaded = library.get(current);
        return loaded == null ? null : loaded.older;
    }

    public boolean open(Path path) {
        try {
            library.load(path);
        } catch (IOException | RuntimeException e) {
            say("could not open " + path.getFileName() + ": " + e.getMessage());
            MoudMod.LOG.warn("could not open animation {}: {}", path, e.getMessage());
            return false;
        }
        if (!path.equals(current)) {
            current = path;
            keys.clear();
            event = -1;
            marker = -1;
            time = 0;
            playing = false;
            lastTime = 0;
        }
        return true;
    }

    public Path create(String name, AnimClip.Space space, String rigName) {
        String clean = name.strip().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (clean.isEmpty()) clean = "clip";
        Path folder = library.folder();
        Path path = folder.resolve(clean + ClipLibrary.EXTENSION);
        for (int n = 2; Files.exists(path) || library.get(path) != null; n++) path = folder.resolve(clean + " " + n + ClipLibrary.EXTENSION);
        AnimClip clip = new AnimClip();
        clip.space = space;
        clip.rig = rigName;
        clip.loop = AnimClip.Loop.LOOP;
        library.create(path, clip);
        current = path;
        keys.clear();
        event = -1;
        marker = -1;
        time = 0;
        save();
        return path;
    }

    public void save() {
        if (current == null) return;
        try {
            library.save(current);
            Animators.forget(res(current));
            PlaceFiles.upload(current);
            say("saved " + library.folder().getParent().relativize(current).toString().replace('\\', '/'));
        } catch (IOException | RuntimeException e) {
            say("could not save: " + e.getMessage());
            MoudMod.LOG.warn("could not save animation {}: {}", current, e.getMessage());
        }
    }

    public void saveAll() {
        Path was = current;
        for (ClipLibrary.Listed listed : library.clips()) {
            ClipLibrary.Open loaded = library.get(listed.path());
            if (loaded == null || !loaded.dirty()) continue;
            current = listed.path();
            save();
        }
        current = was;
    }

    public void edit(String label, Consumer<AnimClip> change) {
        edit(label, null, change);
    }

    public void edit(String label, @Nullable String gesture, Consumer<AnimClip> change) {
        if (current == null) return;
        AnimClip changed = clip().copy();
        change.accept(changed);
        changed.prune();
        document.history().execute(new ClipEdit(this, current, changed, label, gesture));
    }

    public void editFrom(AnimClip start, String label, String gesture, Consumer<AnimClip> change) {
        if (current == null) return;
        AnimClip changed = start.copy();
        change.accept(changed);
        changed.prune();
        document.history().execute(new ClipEdit(this, current, changed, label, gesture));
    }

    public String gesture(String kind) {
        return "anim-" + kind + "-" + (++gestures);
    }

    void replace(Path path, AnimClip clip) {
        ClipLibrary.Open loaded = library.get(path);
        if (loaded == null) throw new IllegalStateException(path.getFileName() + " is not open");
        loaded.clip = clip;
        if (!path.equals(current)) open(path);
        keys.removeIf(ref -> clip.keyAt(ref.joint(), ref.channel(), ref.time()) == null);
        if (event >= clip.events.size()) event = -1;
        if (marker >= clip.markers.size()) marker = -1;
    }

    public double time() {
        return time;
    }

    public double lastTime() {
        return lastTime;
    }

    public void seek(double to) {
        double length = Math.max(0, clip().length);
        time = Math.clamp(snap ? clip().snap(to) : to, 0, Math.max(length, 0));
    }

    public void seekFree(double to) {
        time = Math.clamp(to, 0, Math.max(0, clip().length));
    }

    public boolean playing() {
        return playing;
    }

    public void togglePlay() {
        if (!playing && time >= clip().length - 1e-6) time = 0;
        playing = !playing;
    }

    public void stop() {
        playing = false;
    }

    public boolean looping() {
        return looping;
    }

    public void toggleLooping() {
        looping = !looping;
    }

    public void advance(double dt) {
        lastTime = time;
        if (!playing) return;
        double length = clip().length;
        if (length <= 0) {
            playing = false;
            return;
        }
        double next = time + dt;
        if (next >= length) {
            if (looping) next %= length;
            else {
                next = length;
                playing = false;
            }
        }
        time = next;
    }

    public void markSeen() {
        lastTime = time;
    }

    public List<Double> keyTimes() {
        List<Double> times = new ArrayList<>();
        for (var tracks : clip().channels.values()) {
            for (List<AnimKey> track : tracks.values()) {
                for (AnimKey key : track) {
                    if (times.stream().noneMatch(known -> Math.abs(known - key.time()) < AnimClip.EPSILON)) times.add(key.time());
                }
            }
        }
        times.sort(Double::compare);
        return times;
    }

    public void previousKey() {
        double best = 0;
        for (double at : keyTimes()) {
            if (at < time - 1e-6) best = at;
        }
        time = best;
    }

    public void nextKey() {
        for (double at : keyTimes()) {
            if (at > time + 1e-6) {
                time = at;
                return;
            }
        }
        time = clip().length;
    }

    public @Nullable String joint() {
        return joint;
    }

    public void joint(@Nullable String name) {
        joint = name;
        if (name != null) control = null;
    }

    public @Nullable Instance control() {
        return control != null && control.isAlive() ? control : null;
    }

    public void control(@Nullable Instance chosen) {
        control = chosen;
        if (chosen != null) {
            keys.clear();
            event = -1;
            marker = -1;
        }
    }

    public Set<KeyRef> keys() {
        return keys;
    }

    public void selectKeys(Set<KeyRef> chosen) {
        keys.clear();
        keys.addAll(chosen);
        if (!chosen.isEmpty()) {
            event = -1;
            marker = -1;
            control = null;
        }
    }

    public int event() {
        return event;
    }

    public void event(int index) {
        event = index;
        if (index >= 0) {
            keys.clear();
            marker = -1;
            control = null;
        }
    }

    public int marker() {
        return marker;
    }

    public void marker(int index) {
        marker = index;
        if (index >= 0) {
            keys.clear();
            event = -1;
            control = null;
        }
    }

    public Set<String> expanded() {
        return expanded;
    }

    public void copyKeys() {
        if (keys.isEmpty()) return;
        clipboard = KeyEdits.copy(clip(), keys);
        say(clipboard.size() + (clipboard.size() == 1 ? " key copied" : " keys copied"));
    }

    public void pasteKeys() {
        if (clipboard.isEmpty() || !hasClip()) return;
        List<KeyEdits.Copied> pasting = clipboard;
        double at = snap ? clip().snap(time) : time;
        Set<KeyRef> placed = new LinkedHashSet<>();
        edit("Paste keys", changed -> placed.addAll(KeyEdits.paste(changed, pasting, at)));
        selectKeys(placed);
    }

    public void deleteSelected() {
        if (!keys.isEmpty()) {
            Set<KeyRef> gone = Set.copyOf(keys);
            edit(gone.size() == 1 ? "Delete key" : "Delete keys", changed -> KeyEdits.delete(changed, gone));
            keys.clear();
            return;
        }
        if (event >= 0 && event < clip().events.size()) {
            int index = event;
            edit("Delete event", changed -> changed.events.remove(index));
            event = -1;
            return;
        }
        if (marker >= 0 && marker < clip().markers.size()) {
            int index = marker;
            edit("Delete marker", changed -> changed.markers.remove(index));
            marker = -1;
        }
    }

    public void selectAllKeys() {
        Set<KeyRef> all = new LinkedHashSet<>();
        for (var joints : clip().channels.entrySet()) {
            for (var track : joints.getValue().entrySet()) {
                for (AnimKey key : track.getValue()) all.add(new KeyRef(joints.getKey(), track.getKey(), key.time()));
            }
        }
        selectKeys(all);
    }

    public void mirrorSelected() {
        if (keys.isEmpty()) {
            if (joint != null) mirrorPose();
            return;
        }
        Set<KeyRef> source = Set.copyOf(keys);
        Set<KeyRef> placed = new LinkedHashSet<>();
        edit("Mirror keys", changed -> placed.addAll(KeyEdits.mirror(changed, source)));
        selectKeys(placed);
    }

    public void mirrorPose() {
        if (joint == null || !hasClip()) return;
        String from = joint;
        double at = snap ? clip().snap(time) : time;
        Set<KeyRef> placed = new LinkedHashSet<>();
        edit("Mirror pose", changed -> placed.addAll(KeyEdits.mirrorPose(changed, from, at)));
        if (placed.isEmpty()) say(from + " has no keys to mirror");
        else selectKeys(placed);
    }

    public boolean snap() {
        return snap;
    }

    public void snap(boolean on) {
        snap = on;
    }

    public boolean onion() {
        return onion;
    }

    public void onion(boolean on) {
        onion = on;
    }

    public int onionBefore() {
        return onionBefore;
    }

    public int onionAfter() {
        return onionAfter;
    }

    public void onionCounts(int before, int after) {
        onionBefore = Math.clamp(before, 0, 5);
        onionAfter = Math.clamp(after, 0, 5);
    }

    public boolean local() {
        return local;
    }

    public void local(boolean on) {
        local = on;
    }

    public boolean ikDrag() {
        return ikDrag;
    }

    public void ikDrag(boolean on) {
        ikDrag = on;
    }

    public boolean bones() {
        return bones;
    }

    public void bones(boolean on) {
        bones = on;
    }

    public Tool tool() {
        return tool;
    }

    public void tool(Tool chosen) {
        tool = chosen;
    }

    public double keyTime() {
        return snap ? clip().snap(time) : time;
    }

    public void say(String text) {
        message = text;
        messageAt = System.currentTimeMillis();
    }

    public String message() {
        return System.currentTimeMillis() - messageAt < 6000 ? message : "";
    }

    void closeAll() {
        library.forgetAll();
        current = null;
        keys.clear();
        event = -1;
        marker = -1;
        playing = false;
    }
}
