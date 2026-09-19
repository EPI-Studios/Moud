package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.character.Animation;
import com.meekdev.moud.core.character.AnimationController;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.IKControl;
import com.meekdev.moud.core.character.JointSpring;
import com.meekdev.moud.core.character.JointSprings;
import com.meekdev.moud.core.character.Posing;
import com.meekdev.moud.core.character.Rigs;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Paste;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import com.meekdev.moud.mod.place.ImportSettings;
import com.meekdev.moud.mod.place.Output;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class AnimationWorkspace {

    private static final String LAUNCH_CLIP = "moud.animate";
    private static final String LAUNCH_SELECT = "moud.animate.select";
    private static final String LAUNCH_IMPORT = "moud.animate.import";

    private final SceneDocument document;
    private final ViewportPanel viewport;
    private final AnimationSession session;
    private final PreviewRig rig = new PreviewRig();
    private final AnimationViewport stage;
    private final TopBar topBar;
    private final RigPanel rigPanel;
    private final InspectorPanel inspector;
    private final TimelinePanel timeline;
    private final BbmodelImportDialog importer;
    private final RigDialog rigDialog;
    private final LaunchSteps steps;
    private final Panel viewportWindow;
    private boolean active;
    private boolean placed;
    private int settling;
    private boolean through;
    private @Nullable InstanceTree tree;
    private Map<String, JointPose> pose = Map.of();
    private boolean launched;
    private String wanted = "";
    private final RigReturn returning;
    private @Nullable Paste converting;
    private @Nullable Path convertedClip;

    public AnimationWorkspace(SceneDocument document, IconWidgets icons, ViewportPanel viewport) {
        this.document = document;
        this.viewport = viewport;
        this.session = new AnimationSession(document);
        this.stage = new AnimationViewport(this, session, rig, icons, viewport);
        this.topBar = new TopBar(this, session, rig, icons);
        this.rigPanel = new RigPanel(this, session, rig, icons);
        this.inspector = new InspectorPanel(this, session, rig, icons);
        this.timeline = new TimelinePanel(this, session, icons);
        BbmodelFiles.runtime(new BbmodelWriter(document, viewport::spawnPoint));
        this.importer = new BbmodelImportDialog(new BbmodelFiles(), icons, this);
        this.rigDialog = new RigDialog(this);
        this.returning = new RigReturn(model -> document.ref(model.id())::id);
        document.rigs(this::rigAsset);
        this.steps = new LaunchSteps(this, session);
        this.viewportWindow = new Panel() {
            @Override
            public String id() {
                return "anim-viewport";
            }

            @Override
            public String title() {
                return "Viewport";
            }

            @Override
            public int windowFlags() {
                return viewport.windowFlags();
            }

            @Override
            public void render() {
                viewport.render();
            }
        };
    }

    public boolean active() {
        return active;
    }

    public void toggle() {
        if (active) leave();
        else enter();
    }

    public void enter() {
        if (active) return;
        active = true;
        placed = false;
        viewport.takeover(stage);
        if (!session.hasClip()) openFirst();
    }

    public void leave() {
        if (!active) return;
        active = false;
        session.stop();
        viewport.takeover(null);
        rig.release();
    }

    public List<Panel> panels() {
        return List.of(viewportWindow, rigPanel, inspector, timeline);
    }

    public String viewportId() {
        return viewportWindow.id();
    }

    public String rigId() {
        return rigPanel.id();
    }

    public String inspectorId() {
        return inspector.id();
    }

    public String timelineId() {
        return timeline.id();
    }

    public void renderTopBar() {
        topBar.render();
    }

    public float topBarHeight() {
        return topBar.height();
    }

    public void renderDialogs() {
        importer.render();
        rigDialog.render();
    }

    public void importModel(Path file) {
        importer.open(file);
    }

    void confirmImport() {
        importer.confirm();
    }

    void closeImport() {
        importer.close();
    }

    void askRig(MeshPart mesh) {
        rigDialog.ask(mesh);
    }

    void confirmRig() {
        rigDialog.confirm();
    }

    void makeAnimatable(MeshPart mesh) {
        if (!mesh.isAlive() || mesh.parent() == null || !document.editable(mesh)) return;
        byte[] bytes = PlaceFiles.read(mesh.meshId);
        if (bytes == null) {
            session.say("could not read " + mesh.meshId);
            return;
        }
        Instance holder = Instances.createRoot(new InstanceTree(), Classes.FOLDER, "Scratch");
        Rigging.Made made;
        try {
            made = Rigging.from(mesh, new String(bytes, StandardCharsets.UTF_8), holder);
        } catch (RuntimeException e) {
            session.say("could not make " + mesh.name() + " animatable: " + e.getMessage());
            return;
        }
        if (made == null) {
            session.say(mesh.name() + " has no groups to turn into bones");
            return;
        }
        Map<String, String> fresh = fresh(made);
        Batch edit = Rigging.edit(document, mesh, made.model(), new ClipFiles(Rigging.label(mesh), fresh, true, disk(Rigging.label(mesh))));
        if (document.history().execute(edit).isPresent()) return;
        converting = (Paste) edit.edits().getFirst();
        convertedClip = firstClip(made);
        session.say(made.model().name() + " is animatable" + (fresh.isEmpty() ? "" : ", " + fresh.size() + (fresh.size() == 1 ? " clip" : " clips")
                + " written to " + Rigging.directory(mesh.meshId)));
    }

    private Map<String, String> fresh(Rigging.Made made) {
        List<String> notes = new ArrayList<>(made.notes());
        Map<String, String> fresh = Rigging.fresh(AssetFiles.root(), made.files(), notes);
        for (String note : notes) Output.add(Output.Level.WARN, "editor", made.model().name() + ": " + note);
        return fresh;
    }

    private ClipFiles.Disk disk(String label) {
        return new ClipFiles.Disk(AssetFiles.root(), (res, bytes) -> {
            Animators.forget(res);
            PlaceFiles.upload(res, bytes);
            session.library().rescan();
        }, (res, bytes) -> {
            Animators.forget(res);
            PlaceFiles.remove(res, bytes);
            session.library().rescan();
        }, note -> {
            MoudMod.LOG.warn("{}: {}", label, note);
            Output.add(Output.Level.WARN, "editor", label + ": " + note);
        });
    }

    private @Nullable Path firstClip(Rigging.Made made) {
        for (String file : made.files().keySet()) {
            Path path = AssetFiles.root().resolve(Res.parse(file));
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    private SceneDocument.@Nullable Rigged rigAsset(String res, Instance holder) {
        byte[] bytes = PlaceFiles.read(res);
        if (bytes == null) return null;
        String file = res.substring(res.lastIndexOf('/') + 1);
        Rigging.Made made;
        try {
            made = Rigging.build(new String(bytes, StandardCharsets.UTF_8), res, file.substring(0, file.lastIndexOf('.')), holder);
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("{} could not be rigged: {}", res, e.getMessage());
            return null;
        }
        if (made == null) return null;
        double scale = ImportSettings.of(AssetFiles.root().resolve(Res.parse(res))).scale();
        if (scale > 0 && scale != 1) Rigging.grow(made.model(), scale);
        String label = "Place " + made.model().name();
        return new SceneDocument.Rigged(made.model(), new ClipFiles(label, fresh(made), true, disk(label)));
    }

    private void landConversion() {
        Paste paste = converting;
        if (paste == null || paste.roots().isEmpty()) return;
        if (!(document.find(paste.roots().getFirst()) instanceof Model model)) return;
        converting = null;
        Path clip = convertedClip;
        convertedClip = null;
        if (!active) enter();
        if (clip != null && Files.isRegularFile(clip)) openClip(clip);
        chose();
        rig.borrow(model);
        frameRig();
    }

    Path clipFolder(AnimClip.Space space) {
        Model model = rig.model();
        if (model == null || space == AnimClip.Space.VIEW) return session.library().folder();
        if (model.child("animations") instanceof Instance animations) {
            for (Instance child : animations.children()) {
                if (!(child instanceof Animation animation) || !animation.animationId.startsWith(Res.SCHEME)) continue;
                try {
                    Path folder = AssetFiles.root().resolve(Res.parse(animation.animationId)).getParent();
                    if (folder != null && folder.startsWith(session.library().folder())) return folder;
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
            }
        }
        return AssetFiles.root().resolve(Rigging.directory(Rigging.rigOf(model)));
    }

    List<MeshPart> sceneMeshes() {
        List<MeshPart> found = new ArrayList<>();
        InstanceTree now = ClientScene.tree();
        if (now == null) return found;
        for (Instance instance : now.ofClass(Classes.MESH_PART)) {
            if (instance instanceof MeshPart mesh && mesh.id() > 0 && mesh.isAlive() && Rigging.animatable(mesh) && document.editable(mesh)) found.add(mesh);
        }
        return found;
    }

    public void steps() {
        steps.tick();
    }

    void importFinished(ModelImport.Outcome outcome) {
        session.say(outcome.message());
        session.library().rescan();
        for (Path written : outcome.written()) {
            if (written.getFileName().toString().endsWith(ClipLibrary.EXTENSION) && Files.isRegularFile(written)) {
                if (!active) enter();
                openClip(written);
                return;
            }
        }
    }

    public boolean anyDirty() {
        return session.anyDirty();
    }

    public void open(Path file) {
        if (!active) enter();
        openClip(file);
    }

    void openClip(Path file) {
        Path was = session.path();
        if (session.open(file) && !file.equals(was)) wanted = session.clip().rig;
    }

    void openFirst() {
        List<ClipLibrary.Listed> clips = session.library().clips();
        if (!clips.isEmpty()) openClip(clips.getFirst().path());
    }

    void chose() {
        wanted = "";
        returning.forget();
    }

    public void copy() {
        session.copyKeys();
    }

    public void saveAll() {
        session.saveAll();
    }

    public boolean launching() {
        if (launched) return false;
        String clip = System.getProperty(LAUNCH_CLIP);
        return clip != null && !clip.isBlank();
    }

    public void handleShortcuts() {
        if (!active || ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyAlt() || !session.hasClip()) return;
        if (ImGui.isKeyPressed(ImGuiKey.Space, false)) session.togglePlay();
        if (ImGui.isKeyPressed(ImGuiKey.Home, false)) {
            session.stop();
            session.seek(0);
        }
        if (ImGui.isKeyPressed(ImGuiKey.End, false)) {
            session.stop();
            session.seek(session.clip().length);
        }
        double frame = 1.0 / Math.max(1, session.clip().fps);
        if (ImGui.isKeyPressed(ImGuiKey.LeftArrow, true)) {
            session.stop();
            session.seek(session.time() - frame);
        }
        if (ImGui.isKeyPressed(ImGuiKey.RightArrow, true)) {
            session.stop();
            double before = session.time();
            session.seek(session.time() + frame);
            scrubbed(before, session.time());
        }
    }

    public void paste() {
        session.pasteKeys();
    }

    public void delete() {
        session.deleteSelected();
    }

    public void selectAll() {
        session.selectAllKeys();
    }

    public void duplicate() {
        session.copyKeys();
        session.pasteKeys();
    }

    public boolean hasSelection() {
        return !session.keys().isEmpty() || session.event() >= 0 || session.marker() >= 0;
    }

    public void save() {
        session.save();
    }

    public String status() {
        String message = session.message();
        if (!message.isEmpty()) return message;
        return session.hasClip() ? "animating " + session.name() + ClipLibrary.EXTENSION : "no clip open";
    }

    Map<String, JointPose> pose() {
        return pose;
    }

    boolean through() {
        return through;
    }

    void through(boolean on) {
        through = on;
        if (!on) frameRig();
    }

    ViewportPanel viewport() {
        return viewport;
    }

    SceneDocument document() {
        return document;
    }

    String rigName() {
        return rig.label();
    }

    List<Model> sceneModels() {
        List<Model> found = new ArrayList<>();
        InstanceTree now = ClientScene.tree();
        if (now == null) return found;
        for (Instance instance : now.ofClass(Classes.ANIMATION_CONTROLLER)) {
            if (instance instanceof AnimationController && instance.parent() instanceof Model model && model.id() > 0 && model.isAlive()
                    && !Rigs.jointsIn(model).isEmpty() && !found.contains(model)) {
                found.add(model);
            }
        }
        return found;
    }

    List<Skeletons.Joint> skeleton() {
        return Skeletons.of(session.clip(), rig.model() == null ? null : rig.skeleton());
    }

    boolean retargets() {
        return rig.retargets() && session.clip().space != AnimClip.Space.VIEW;
    }

    List<Instance> controls() {
        List<Instance> found = new ArrayList<>();
        InstanceTree now = ClientScene.tree();
        if (now == null || session.clip().space == AnimClip.Space.VIEW) return found;
        Set<Instance> joints = new HashSet<>(rig.rigJoints().values());
        if (joints.isEmpty()) return found;
        for (Instance instance : now.ofClass(Classes.IK_CONTROL)) {
            if (instance instanceof IKControl control && (joints.contains(control.endEffector) || joints.contains(control.chainRoot))) found.add(control);
        }
        for (Instance instance : now.ofClass(Classes.JOINT_SPRING)) {
            if (instance instanceof JointSpring spring && joints.contains(JointSprings.joint(spring))) found.add(spring);
        }
        return found;
    }

    private void adoptWanted() {
        if (wanted.isEmpty() || session.clip().space == AnimClip.Space.VIEW) return;
        if (wanted.equals("player")) {
            wanted = "";
            return;
        }
        for (Model model : sceneModels()) {
            if (Rigging.rigs(model, wanted)) {
                rig.borrow(model);
                wanted = "";
                frameRig();
                return;
            }
        }
    }

    private void comeBack() {
        returning.seen(rig.model(), session.path());
        if (!returning.waiting()) return;
        RigReturn.Back back = returning.back(sceneModels());
        if (back == null) return;
        rig.borrow(back.model());
        Path clip = back.clip();
        Path open = session.path();
        if (clip != null && !clip.equals(open) && (open == null || !Files.isRegularFile(open)) && Files.isRegularFile(clip)) session.open(clip);
        frameRig();
    }

    List<Character> sceneCharacters() {
        List<Character> found = new ArrayList<>();
        InstanceTree now = ClientScene.tree();
        if (now == null) return found;
        for (Instance instance : now.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character && character.id() > 0 && character.isAlive()) found.add(character);
        }
        return found;
    }

    public void tick() {
        if (launched) return;
        String clip = System.getProperty(LAUNCH_CLIP);
        if (clip == null || clip.isBlank()) {
            launched = true;
            return;
        }
        if (ClientScene.world() == null) return;
        launched = true;
        enter();
        Path file = session.library().folder().resolve(clip.endsWith(ClipLibrary.EXTENSION) ? clip : clip + ClipLibrary.EXTENSION);
        if (Files.isRegularFile(file)) openClip(file);
        String select = System.getProperty(LAUNCH_SELECT, "");
        if (select.startsWith("event:")) {
            List<AnimClip.Event> events = session.clip().events;
            for (int n = 0; n < events.size(); n++) {
                if (events.get(n).name().equals(select.substring("event:".length()))) session.event(n);
            }
        } else if (!select.isBlank()) {
            session.joint(select);
            double at = 0;
            for (AnimKey key : session.clip().keys(select, Channel.ROTATION)) {
                if (key.time() > 0.3) {
                    at = key.time();
                    break;
                }
            }
            session.seek(at);
            if (session.clip().keyAt(select, Channel.ROTATION, at) != null) session.selectKeys(Set.of(new KeyRef(select, Channel.ROTATION, at)));
            session.expanded().add(select);
        }
        String model = System.getProperty(LAUNCH_IMPORT, "");
        if (!model.isBlank()) importModel(session.library().folder().getParent().resolve(model));
    }

    public void frame() {
        if (!active) return;
        InstanceTree now = ClientScene.tree();
        if (now != tree) {
            tree = now;
            rig.release();
            returning.forget();
            placed = false;
        }
        if (now == null) return;
        if (placed && rig.body() == null && rig.model() == null) placed = false;
        if (!placed && ++settling > 3) place();
        double before = session.time();
        session.advance(ImGui.getIO().getDeltaTime());
        AnimClip clip = session.clip();
        double after = session.time();
        if (after > before) EventPreview.crossed(clip, before, after, rig.world().position().add(new Vector3(0, 1.2, 0)));
        else if (session.playing() && after < before) EventPreview.crossed(clip, -1e-9, after, rig.world().position().add(new Vector3(0, 1.2, 0)));
        landConversion();
        adoptWanted();
        comeBack();
        List<String> joints = Skeletons.names(skeleton());
        pose = JointPose.all(clip, session.compiled(), joints, after, retargets());
        if (clip.space == AnimClip.Space.VIEW) {
            CFrame eye = rig.eye();
            rig.poseView(pose, eye, !through);
            if (through) viewport.lookFrom(rig.viewPivot("camera", pose, eye));
        } else {
            double dt = Math.min(0.1, ImGui.getIO().getDeltaTime());
            rig.pose(pose, () -> Posing.solve(now, new HashSet<>(rig.rigJoints().values()), dt));
        }
    }

    void scrubbed(double from, double to) {
        if (to > from) EventPreview.crossed(session.clip(), from, to, rig.world().position().add(new Vector3(0, 1.2, 0)));
    }

    private void place() {
        CFrame eye = viewport.view();
        Vector3 ahead = eye.lookVector();
        Vector3 flat = new Vector3(ahead.x(), 0, ahead.z());
        if (flat.lengthSq() < 1e-6) flat = new Vector3(0, 0, -1);
        flat = flat.normalize();
        Vector3 feet = eye.position().add(flat.mul(3.2)).add(new Vector3(0, -1.4, 0));
        double yaw = Math.toDegrees(Math.atan2(-flat.x(), -flat.z())) + 180;
        if (!rig.ensure(feet, yaw)) return;
        placed = true;
        settling = 0;        frameRig();
    }

    void frameRig() {
        if (rig.body() == null && rig.model() == null) return;
        double size = rig.size();
        Vector3 centre = rig.centre();
        Vector3 away = viewport.view().position().sub(centre);
        away = new Vector3(away.x(), 0, away.z());
        if (away.lengthSq() < 1e-6) away = rig.world().lookVector();
        viewport.lookAt(centre.add(away.normalize().mul(4 * size)).add(new Vector3(0, 0.6 * size, 0)), centre);
        viewport.frame(centre, 2.1 * size);
    }

    void lookAtRig(Vector3 from) {
        Vector3 centre = rig.centre();
        viewport.lookAt(centre.add(from), centre);
    }

    void restage() {
        rig.release();
        placed = false;
    }
}
