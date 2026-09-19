package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.CharacterDisplay;
import com.meekdev.moud.core.character.Limb;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.character.Rigs;
import com.meekdev.moud.core.character.ViewModels;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.instance.Motor;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

final class PreviewRig {

    static final String NAME = "AnimationPreview";

    record Skin(String label, String texture, boolean slim) {}

    static final List<Skin> SKINS = List.of(
            new Skin("steve", "minecraft:textures/entity/player/wide/steve.png", false),
            new Skin("alex", "minecraft:textures/entity/player/slim/alex.png", true),
            new Skin("ari", "minecraft:textures/entity/player/wide/ari.png", false),
            new Skin("efe", "minecraft:textures/entity/player/slim/efe.png", true),
            new Skin("kai", "minecraft:textures/entity/player/wide/kai.png", false),
            new Skin("makena", "minecraft:textures/entity/player/slim/makena.png", true),
            new Skin("noor", "minecraft:textures/entity/player/slim/noor.png", true),
            new Skin("sunny", "minecraft:textures/entity/player/wide/sunny.png", false),
            new Skin("zuri", "minecraft:textures/entity/player/wide/zuri.png", false));

    record Box(CFrame frame, Vector3 size) {}

    private static final String[] VIEW_LIMBS = {"rightArm", "leftArm"};
    private static final double EYE_HEIGHT = 1.62;

    private @Nullable Character preview;
    private @Nullable Character borrowed;
    private boolean borrowedAnimate;
    private final Map<Joint, CFrame> borrowedTransforms = new HashMap<>();
    private final Map<Joint, Vector3> borrowedScales = new HashMap<>();
    private @Nullable Model model;
    private final Map<Instance, CFrame> modelTransforms = new HashMap<>();
    private final Map<Instance, Vector3> modelScales = new HashMap<>();
    private final List<Instance> lived = new ArrayList<>();
    private Skin skin = SKINS.get(1);
    private boolean ownSkin = true;
    private String heldItem = "";
    private boolean viewShown;

    @Nullable Character body() {
        if (model() != null) return null;
        if (borrowed != null && borrowed.isAlive()) return borrowed;
        return preview != null && preview.isAlive() ? preview : null;
    }

    boolean borrowing() {
        return borrowed != null && borrowed.isAlive() || model() != null;
    }

    @Nullable Model model() {
        return model != null && model.isAlive() ? model : null;
    }

    boolean retargets() {
        return model() == null;
    }

    @Nullable Character preview() {
        return preview != null && preview.isAlive() ? preview : null;
    }

    String label() {
        if (model() != null) return model.name();
        if (borrowing()) return borrowed.name();
        return "Player rig · " + (ownSkin ? "you" : skin.label());
    }

    Skin skin() {
        return skin;
    }

    boolean ownSkin() {
        return ownSkin;
    }

    void skin(@Nullable Skin chosen) {
        ownSkin = chosen == null;
        if (chosen != null) skin = chosen;
        giveBack();
        dress();
    }

    void heldItem(String item) {
        heldItem = item;
        if (preview() != null) preview.rightItemOverride = item;
    }

    String heldItem() {
        return heldItem;
    }

    void borrow(Model chosen) {
        if (chosen == model) return;
        giveBack();
        model = chosen;
        for (Instance joint : Rigs.jointsIn(chosen).values()) {
            modelTransforms.put(joint, Rigs.transform(joint));
            modelScales.put(joint, Rigs.scale(joint));
        }
    }

    void borrow(Character character) {
        if (character == borrowed) return;
        giveBack();
        borrowed = character;
        borrowedAnimate = character.animate;
        character.animate = false;
        if (character.child(Rig.JOINTS) instanceof Instance joints) {
            for (Instance child : joints.children()) {
                if (child instanceof Joint joint) {
                    borrowedTransforms.put(joint, joint.transform);
                    borrowedScales.put(joint, joint.scale);
                }
            }
        }
    }

    void giveBack() {
        unlive();
        if (model != null) {
            modelTransforms.forEach((joint, transform) -> {
                if (joint instanceof Bone bone) bone.transform = transform;
                else if (joint instanceof Joint hinge) hinge.transform = transform;
            });
            modelScales.forEach((joint, scale) -> {
                if (joint instanceof Bone bone) bone.scale = scale;
                else if (joint instanceof Joint hinge) hinge.scale = scale;
            });
            modelTransforms.clear();
            modelScales.clear();
            model = null;
        }
        if (borrowed == null) return;
        Character character = borrowed;
        borrowed = null;
        if (!character.isAlive()) {
            borrowedTransforms.clear();
            borrowedScales.clear();
            return;
        }
        character.animate = borrowedAnimate;
        borrowedTransforms.forEach((joint, transform) -> joint.transform = transform);
        borrowedScales.forEach((joint, scale) -> joint.scale = scale);
        borrowedTransforms.clear();
        borrowedScales.clear();
        Rig.apply(character);
    }

    boolean ensure(Vector3 feet, double yawDegrees) {
        Instance world = ClientScene.world();
        if (world == null) return false;
        if (preview != null && preview.isAlive() && preview.parent() == world) return true;
        preview = Instances.createLocal(Classes.CHARACTER, world, NAME);
        preview.animate = false;
        preview.cframe = new CFrame(feet, CFrame.angles(0, Math.toRadians(yawDegrees), 0).rotation());
        dress();
        return true;
    }

    void place(Vector3 feet, double yawDegrees) {
        if (preview() == null) return;
        preview.cframe = new CFrame(feet, CFrame.angles(0, Math.toRadians(yawDegrees), 0).rotation());
    }

    private void dress() {
        if (preview() == null) return;
        Appearance look = Rig.appearance(preview);
        if (look == null) return;
        look.display = CharacterDisplay.MODEL;
        LocalPlayer player = Minecraft.getInstance().player;
        if (ownSkin && player != null) {
            look.skin = player.getSkin().body().texturePath().toString();
            look.slim = player.getSkin().model() == PlayerModelType.SLIM;
        } else {
            look.skin = skin.texture();
            look.slim = skin.slim();
        }
        preview.rightItemOverride = heldItem;
        Rig.apply(preview);
    }

    void release() {
        giveBack();
        if (preview != null && preview.isAlive()) {
            Motion motion = ClientScene.motion();
            for (Instance instance : lived) motion.unlive(instance);
            Instances.destroy(preview);
        }
        lived.clear();
        preview = null;
    }

    private void unlive() {
        Motion motion = ClientScene.motion();
        for (Instance instance : lived) motion.unlive(instance);
        lived.clear();
    }

    Map<String, Instance> rigJoints() {
        Model chosen = model();
        if (chosen != null) return Rigs.jointsIn(chosen);
        return new LinkedHashMap<>(joints());
    }

    List<Skeletons.Joint> skeleton() {
        List<Skeletons.Joint> out = new ArrayList<>();
        Map<String, Instance> joints = rigJoints();
        for (Map.Entry<String, Instance> entry : joints.entrySet()) {
            Instance above = Rigs.rigParent(entry.getValue());
            int depth = 0;
            for (Instance at = above; at != null && depth < 64; at = Rigs.rigParent(at)) depth++;
            out.add(new Skeletons.Joint(entry.getKey(), above == null ? "" : above.name(), depth));
        }
        return out;
    }

    Map<String, Joint> joints() {
        Map<String, Joint> found = new LinkedHashMap<>();
        Character character = body();
        if (character == null || !(character.child(Rig.JOINTS) instanceof Instance joints)) return found;
        for (Instance child : joints.children()) {
            if (child instanceof Motor joint) found.put(joint.name(), joint);
        }
        return found;
    }

    Map<Integer, String> limbIds() {
        Map<Integer, String> ids = new HashMap<>();
        for (Joint joint : joints().values()) {
            if (joint.part1 instanceof Part part && part.visible) ids.put(part.id(), joint.name());
        }
        return ids;
    }

    void pose(Map<String, JointPose> pose, Runnable controls) {
        Model chosen = model();
        if (chosen != null) {
            poseModel(chosen, pose);
            controls.run();
            settleModel();
            return;
        }
        Character character = body();
        if (character == null) return;
        showBody(true);
        for (Joint joint : joints().values()) {
            JointPose at = pose.getOrDefault(joint.name(), JointPose.REST);
            joint.transform = at.transform();
            joint.scale = at.scale();
        }
        if (borrowing()) controls.run();
        Rig.apply(character);
        live(character);
        liveItems(pose, null);
    }

    private void poseModel(Model chosen, Map<String, JointPose> pose) {
        for (Map.Entry<String, Instance> entry : Rigs.jointsIn(chosen).entrySet()) {
            JointPose at = pose.getOrDefault(entry.getKey(), JointPose.REST);
            boolean still = at == JointPose.REST;
            CFrame transform = still ? modelTransforms.getOrDefault(entry.getValue(), CFrame.IDENTITY) : at.transform();
            Vector3 scale = still ? modelScales.getOrDefault(entry.getValue(), Vector3.ONE) : at.scale();
            if (entry.getValue() instanceof Bone bone) {
                bone.transform = transform;
                bone.scale = scale;
            } else if (entry.getValue() instanceof Joint hinge) {
                hinge.transform = transform;
                hinge.scale = scale;
            }
        }
    }

    private void settleModel() {
        Model chosen = model();
        if (chosen == null) return;
        Motion motion = ClientScene.motion();
        for (Instance joint : Rigs.jointsIn(chosen).values()) {
            if (joint instanceof Bone bone) {
                motion.live(bone, Transforms.local(bone));
                if (!lived.contains(bone)) lived.add(bone);
            } else if (joint instanceof Joint hinge && hinge.part1 instanceof Spatial held && held.isAlive()) {
                motion.live(held, Transforms.local(held));
                if (!lived.contains(held)) lived.add(held);
            }
        }
    }

    void poseView(Map<String, JointPose> pose, CFrame camera, boolean ghost) {
        Character character = preview();
        if (character == null) return;
        if (borrowing()) giveBack();
        showBody(ghost);
        for (Joint joint : joints().values()) {
            JointPose at = pose.getOrDefault(joint.name(), JointPose.REST);
            joint.transform = at.transform();
            joint.scale = at.scale();
        }
        Rig.apply(character);
        live(character);
        CFrame inverse = Transforms.world(character).inverse();
        for (String name : VIEW_LIMBS) {
            if (!(character.child(name) instanceof Part arm)) continue;
            Box box = viewBox(name, pose, camera);
            if (box == null) continue;
            ClientScene.motion().live(arm, inverse.mul(box.frame()));
            if (!lived.contains(arm)) lived.add(arm);
        }
        liveItems(pose, camera);
    }

    private void showBody(boolean whole) {
        Character character = preview();
        if (character == null || borrowing()) return;
        if (whole == !viewShown) return;
        viewShown = !whole;
        for (String name : Rig.limbs()) {
            if (!(character.child(name) instanceof Limb limb)) continue;
            boolean arm = name.equals("rightArm") || name.equals("leftArm");
            limb.visible = whole || arm;
        }
    }

    boolean viewShown() {
        return viewShown;
    }

    private void live(Character character) {
        Motion motion = ClientScene.motion();
        for (Joint joint : joints().values()) {
            if (!(joint.part1 instanceof Spatial held) || !held.isAlive()) continue;
            motion.live(held, Transforms.local(held));
            if (!lived.contains(held)) lived.add(held);
        }
        if (!lived.contains(character)) {
            motion.live(character, Transforms.local(character));
            lived.add(character);
        } else {
            motion.live(character, Transforms.local(character));
        }
    }

    private void liveItems(Map<String, JointPose> pose, @Nullable CFrame camera) {
        Character character = body();
        if (character == null) return;
        for (String side : new String[] {"right", "left"}) {
            if (!(character.child(side + "Arm") instanceof Part arm) || !(arm.child(Rig.GRIP) instanceof Spatial grip)) continue;
            JointPose item = pose.get(side + "Item");
            CFrame local;
            Box held = camera == null ? null : viewBox(side + "Arm", pose, camera);
            if (held != null) {
                local = held.frame().inverse().mul(viewPivot(side + "Item", pose, camera));
            } else {
                CFrame rest = Transforms.local(grip);
                local = item == null ? rest : rest.mul(item.transform());
            }
            ClientScene.motion().live(grip, local);
            if (!lived.contains(grip)) lived.add(grip);
        }
    }

    CFrame eye() {
        Character character = preview();
        if (character == null) return CFrame.IDENTITY;
        return Transforms.world(character).mul(CFrame.at(0, EYE_HEIGHT * character.scale, 0));
    }

    CFrame world() {
        Model chosen = model();
        if (chosen != null) return Transforms.world(chosen);
        Character character = body();
        return character == null ? CFrame.IDENTITY : Transforms.world(character);
    }

    double size() {
        Model chosen = model();
        if (chosen != null) return chosen.primaryPart instanceof Part part ? Math.max(0.5, part.size.length() * 0.5) : 1.0;
        Character character = body();
        return character == null ? 1.0 : character.scale;
    }

    Vector3 centre() {
        Model chosen = model();
        if (chosen != null && chosen.primaryPart instanceof Part part) return Transforms.world(part).position();
        return world().position().add(new Vector3(0, size(), 0));
    }

    @Nullable CFrame boneFrame(String name, Map<String, JointPose> pose) {
        Model chosen = model();
        if (chosen == null) return null;
        Instance joint = Rigs.jointsIn(chosen).get(name);
        if (joint instanceof Bone bone) return posedBone(bone, pose, 0).mul(pose.getOrDefault(name, JointPose.REST).transform());
        return joint == null ? null : Rigs.frame(joint);
    }

    private CFrame posedBone(Bone bone, Map<String, JointPose> pose, int depth) {
        Instance above = bone.parent();
        CFrame base = above instanceof Bone parentBone && depth < 64
                ? posedBone(parentBone, pose, depth + 1).mul(pose.getOrDefault(parentBone.name(), JointPose.REST).transform())
                : above == null ? CFrame.IDENTITY : Transforms.world(above);
        return base.mul(bone.cframe);
    }

    @Nullable CFrame parentFrame(String name, Map<String, JointPose> pose) {
        Model chosen = model();
        if (chosen != null) {
            Instance joint = Rigs.jointsIn(chosen).get(name);
            if (joint instanceof Bone bone) return posedBone(bone, pose, 0);
            return joint == null ? null : Rigs.parentFrame(joint);
        }
        Joint joint = joints().get(name);
        if (joint == null || !(joint.part1 instanceof Spatial held)) return null;
        Instance parent = held.parent();
        if (parent == null) return null;
        return posedWorld(parent, pose).mul(base(joint, pose)).mul(joint.c0);
    }

    @Nullable CFrame pivotFrame(String name, Map<String, JointPose> pose) {
        CFrame parent = parentFrame(name, pose);
        if (parent == null) return null;
        return parent.mul(pose.getOrDefault(name, JointPose.REST).transform());
    }

    @Nullable Box box(String name, Map<String, JointPose> pose) {
        Joint joint = joints().get(name);
        if (joint == null || !(joint.part1 instanceof Part part)) return null;
        return new Box(posedWorld(part, pose), part.size);
    }

    private CFrame posedWorld(Instance instance, Map<String, JointPose> pose) {
        Character character = body();
        if (instance == character || instance.parent() == null) return Transforms.world(instance);
        return posedWorld(instance.parent(), pose).mul(posedLocal(instance, pose));
    }

    private CFrame posedLocal(Instance instance, Map<String, JointPose> pose) {
        Joint joint = motorOf(instance);
        if (joint == null) return Transforms.local(instance);
        CFrame frame = base(joint, pose).mul(joint.c0).mul(pose.getOrDefault(joint.name(), JointPose.REST).transform());
        if (!joint.c1.equals(CFrame.IDENTITY)) frame = frame.mul(joint.c1.inverse());
        Vector3 pivot = instance instanceof Spatial spatial ? spatial.pivot : Vector3.ZERO;
        return pivot.equals(Vector3.ZERO) ? frame : frame.mul(CFrame.at(pivot.neg()));
    }

    private CFrame base(Joint joint, Map<String, JointPose> pose) {
        if (joint.part0 == null || joint.part1 == null) return CFrame.IDENTITY;
        if (joint.part1.parent() == joint.part0) return CFrame.IDENTITY;
        if (joint.part0.parent() == joint.part1.parent()) return posedLocal(joint.part0, pose);
        return posedWorld(joint.part1.parent(), pose).inverse().mul(posedWorld(joint.part0, pose));
    }

    private @Nullable Joint motorOf(Instance instance) {
        for (Joint joint : joints().values()) {
            if (joint.part1 == instance) return joint;
        }
        return null;
    }

    @Nullable Box viewBox(String arm, Map<String, JointPose> pose, CFrame camera) {
        Character character = preview();
        if (character == null || !(character.child(arm) instanceof Part part)) return null;
        CFrame pivot = viewPivot(arm, pose, camera);
        return new Box(part.pivot.equals(Vector3.ZERO) ? pivot : pivot.mul(CFrame.at(part.pivot.neg())), part.size);
    }

    CFrame viewParent(String joint, Map<String, JointPose> pose, CFrame camera) {
        if (joint.equals(ViewModels.CAMERA)) return camera;
        if (joint.endsWith("Item")) {
            String side = joint.substring(0, joint.length() - "Item".length());
            return viewPivot(side + "Arm", pose, camera).mul(ViewModels.itemRest(side.equals("right")));
        }
        CFrame eye = camera.mul(pose.getOrDefault(ViewModels.CAMERA, JointPose.REST).transform());
        return eye.mul(ViewModels.armRest(joint.startsWith("right")));
    }

    CFrame viewPivot(String joint, Map<String, JointPose> pose, CFrame camera) {
        return viewParent(joint, pose, camera).mul(pose.getOrDefault(joint, JointPose.REST).transform());
    }
}
