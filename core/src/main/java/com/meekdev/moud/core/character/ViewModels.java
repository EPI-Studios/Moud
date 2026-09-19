package com.meekdev.moud.core.character;

import com.meekdev.moud.core.asset.BbmodelImport;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ViewModels {

    public static final String CAMERA = "camera";
    public static final String RIGHT_ARM = "rightArm";
    public static final String LEFT_ARM = "leftArm";
    public static final String RIGHT_ITEM = "rightItem";
    public static final String LEFT_ITEM = "leftItem";

    private static final double PX = 1.0 / 16.0;
    private static final int DEEPEST = 64;

    private static final Set<String> BODY_ONLY = Set.of("root", "torso", "head", "rightLeg", "leftLeg", "cape");

    private record Built(String model, Bone camera, BbmodelImport.Rig rig) {}

    private record Read(String text, BbmodelImport.Rig rig) {}

    private static Function<String, String> files = path -> null;
    private static Consumer<String> notes = note -> {};

    private static final Map<ViewModel, Built> BUILT = new WeakHashMap<>();
    private static final Map<String, Read> MODELS = new HashMap<>();
    private static final Set<String> TOLD = new HashSet<>();

    private ViewModels() {}

    public static void files(Function<String, String> reader, Consumer<String> told) {
        files = reader;
        notes = told;
        MODELS.clear();
        TOLD.clear();
    }

    public static void forget() {
        BUILT.clear();
        MODELS.clear();
        TOLD.clear();
    }

    public static ViewModel of(Character character) {
        return character != null && character.child(Rig.VIEW_MODEL) instanceof ViewModel view ? view : null;
    }

    public static ViewModel holding(Instance instance) {
        int depth = 0;
        for (Instance at = instance; at != null && depth++ < DEEPEST; at = at.parent()) {
            if (at instanceof ViewModel view) return view;
        }
        return null;
    }

    public static boolean inside(Instance instance) {
        return holding(instance) != null;
    }

    public static Animator animator(ViewModel view) {
        return view.child(Rig.ANIMATOR) instanceof Animator animator ? animator : null;
    }

    public static CFrame armRest(boolean right) {
        double side = right ? 1 : -1;
        return new CFrame(new Vector3(side * 0.504, -0.8186, -0.6633),
                new Quat(0.67115, side * 0.11699, side * 0.56561, 0.46472).normalize());
    }

    public static CFrame itemRest(boolean right) {
        return Rig.grip(right ? 1 : -1, 1);
    }

    public static void ensure(ViewModel view) {
        Built built = BUILT.get(view);
        if (built != null && built.camera().isAlive() && built.camera().parent() == view && built.model().equals(view.model)) return;
        for (Instance child : List.copyOf(view.children())) {
            if (child instanceof Bone) Instances.destroy(child);
        }
        BbmodelImport.Rig rig = view.model.isEmpty() ? null : read(view.model);
        Bone camera = rig == null ? skinArms(view) : fromModel(view, rig);
        BUILT.put(view, new Built(view.model, camera, rig));
    }

    public static BbmodelImport.Rig model(ViewModel view) {
        Built built = BUILT.get(view);
        return built == null ? null : built.rig();
    }

    public static String modelText(String res) {
        Read known = MODELS.get(res);
        return known == null ? null : known.text();
    }

    private static BbmodelImport.Rig read(String res) {
        String text = files.apply(res);
        Read known = MODELS.get(res);
        if (known != null && (text == null || text.equals(known.text()))) return known.rig();
        if (text == null) {
            tell(res + " is not in the place, the view model uses the skin arms");
            return null;
        }
        try {
            String name = res.substring(res.lastIndexOf('/') + 1);
            BbmodelImport.Rig rig = BbmodelImport.read(BbmodelImport.modernize(text), name);
            MODELS.put(res, new Read(text, rig));
            return rig;
        } catch (RuntimeException e) {
            tell(res + " cannot be read as a view model: " + e.getMessage());
            return null;
        }
    }

    private static void tell(String note) {
        if (TOLD.add(note)) notes.accept(note);
    }

    private static Bone skinArms(ViewModel view) {
        Bone camera = bone(view, CAMERA, CFrame.IDENTITY);
        for (boolean right : new boolean[] {true, false}) {
            Bone arm = bone(camera, right ? RIGHT_ARM : LEFT_ARM, armRest(right));
            bone(arm, right ? RIGHT_ITEM : LEFT_ITEM, itemRest(right));
        }
        return camera;
    }

    private static Bone fromModel(ViewModel view, BbmodelImport.Rig rig) {
        BbmodelImport.Group eye = null;
        for (BbmodelImport.Group group : rig.groups()) {
            if (group.parent() == null && group.name().equals(CAMERA)) eye = group;
        }
        CFrame eyeRest = eye == null ? CFrame.IDENTITY : frame(eye.origin(), eye.rotation());
        Bone camera = bone(view, CAMERA, eyeRest);
        Map<String, Bone> made = new HashMap<>();
        Map<String, BbmodelImport.Group> byUuid = new HashMap<>();
        if (eye != null) made.put(eye.uuid(), camera);
        for (BbmodelImport.Group group : rig.groups()) byUuid.put(group.uuid(), group);
        for (BbmodelImport.Group group : rig.groups()) {
            if (group == eye) continue;
            Bone above = group.parent() == null ? null : made.get(group.parent());
            BbmodelImport.Group parent = group.parent() == null ? null : byUuid.get(group.parent());
            CFrame rest;
            if (above == null || parent == null) {
                above = camera;
                rest = eyeRest.inverse().mul(frame(group.origin(), group.rotation()));
            } else {
                rest = new CFrame(group.origin().sub(parent.origin()).mul(PX), Clip.euler(group.rotation(), BbmodelImport.EULER));
            }
            made.put(group.uuid(), bone(above, group.name(), rest));
        }
        return camera;
    }

    private static CFrame frame(Vector3 origin, Vector3 rotation) {
        return new CFrame(origin.mul(PX), Clip.euler(rotation, BbmodelImport.EULER));
    }

    private static Bone bone(Instance parent, String name, CFrame rest) {
        Bone bone = Instances.createLocal(Classes.BONE, parent, name);
        bone.cframe = rest;
        return bone;
    }

    public static Bone camera(ViewModel view) {
        Built built = BUILT.get(view);
        if (built != null && built.camera().isAlive() && built.camera().parent() == view) return built.camera();
        return view.child(CAMERA) instanceof Bone bone ? bone : null;
    }

    public static Map<String, Bone> joints(ViewModel view) {
        Map<String, Bone> joints = new LinkedHashMap<>();
        gather(view, joints, 0);
        return joints;
    }

    private static void gather(Instance under, Map<String, Bone> joints, int depth) {
        if (depth > DEEPEST) return;
        for (Instance child : under.children()) {
            if (child instanceof Bone bone) {
                joints.putIfAbsent(bone.name(), bone);
                gather(bone, joints, depth + 1);
            }
        }
    }

    public static void animate(ViewModel view, double dt) {
        ensure(view);
        for (Bone bone : joints(view).values()) {
            Rigs.transform(bone, CFrame.IDENTITY);
            Rigs.scale(bone, Vector3.ONE);
        }
        Animator animator = animator(view);
        if (animator == null) return;
        for (Instance child : animator.children()) {
            if (child instanceof AnimationTrack track && track.animation != null) Animators.advance(track, dt, false);
        }
        Animators.pose(animator, true, false);
    }

    public static void solve(ViewModel view, double dt) {
        if (view.tree() == null) return;
        List<IKControl> controls = new ArrayList<>();
        for (Instance instance : view.tree().ofClass(Classes.IK_CONTROL)) {
            if (instance instanceof IKControl control && control.enabled && control.weight > 0
                    && Rigs.posable(control.endEffector) && control.endEffector.isAlive()
                    && holding(control.endEffector) == view) {
                controls.add(control);
            }
        }
        controls.sort(Comparator.comparingDouble(control -> control.priority));
        for (IKControl control : controls) IKControls.solve(control, dt, false);
    }

    public static boolean active(ViewModel view) {
        if (!view.visible) return false;
        if (view.enabled || !view.model.isEmpty()) return true;
        Animator animator = animator(view);
        return animator != null && Animators.moving(animator);
    }

    public static CFrame offset(ViewModel view) {
        Bone camera = camera(view);
        return camera == null ? CFrame.IDENTITY : camera.transform;
    }

    public static CFrame inView(ViewModel view, Instance joint) {
        Bone camera = camera(view);
        CFrame eye = camera == null ? Transforms.world(view) : Transforms.world(camera);
        return eye.inverse().mul(Transforms.world(joint));
    }

    public static String refusal(Instance animator, Instance animation, Clip clip) {
        if (clip.space() != AnimationSpace.VIEW || inside(animator)) return null;
        return animation.name() + " is a first person clip, load it with body.viewModel.animator";
    }

    public static String warning(Instance animator, Instance animation, Clip clip) {
        if (clip.space() != AnimationSpace.BODY || !inside(animator)) return null;
        for (String joint : clip.joints().keySet()) {
            String body = Retarget.joint(joint);
            if (BODY_ONLY.contains(joint) || body != null && BODY_ONLY.contains(body)) {
                return animation.name() + " is a body clip, on the view model it only moves the joints it shares with it";
            }
        }
        return null;
    }
}
