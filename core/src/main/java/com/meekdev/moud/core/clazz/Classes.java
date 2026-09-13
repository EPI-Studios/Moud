package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.core.instance.AnimationTrack;
import com.meekdev.moud.core.instance.Appearance;
import com.meekdev.moud.core.instance.Animator;
import com.meekdev.moud.core.instance.Armour;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.BillboardGui;
import com.meekdev.moud.core.instance.BoolValue;
import com.meekdev.moud.core.instance.NumberValue;
import com.meekdev.moud.core.instance.ObjectValue;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.StringValue;
import com.meekdev.moud.core.instance.UnreliableRemote;
import com.meekdev.moud.core.instance.Value;
import com.meekdev.moud.core.instance.Vector3Value;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.Cape;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Folder;
import com.meekdev.moud.core.instance.Frame;
import com.meekdev.moud.core.instance.GuiObject;
import com.meekdev.moud.core.instance.Humanoid;
import com.meekdev.moud.core.instance.ImageLabel;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Limb;
import com.meekdev.moud.core.instance.Motor;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.ScreenGui;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.SurfaceGui;
import com.meekdev.moud.core.instance.TextButton;
import com.meekdev.moud.core.instance.TextLabel;
import com.meekdev.moud.core.instance.Wings;
import java.util.List;

public final class Classes {

    public static final ClassDef<Folder> FOLDER = ClassDef.of("Folder", null, Folder.class, Folder::new);
    public static final ClassDef<Spatial> SPATIAL = ClassDef.of("Spatial", null, Spatial.class, Spatial::new);
    public static final ClassDef<Part> PART = ClassDef.of("Part", SPATIAL, Part.class, Part::new);
    public static final ClassDef<Character> CHARACTER =
            ClassDef.of("Character", SPATIAL, Character.class, Character::new);
    public static final ClassDef<Camera> CAMERA = ClassDef.of("Camera", SPATIAL, Camera.class, Camera::new);
    // not a spatial: a joint is not somewhere, it is how two things are held together
    public static final ClassDef<Animator> ANIMATOR =
            ClassDef.of("Animator", null, Animator.class, Animator::new);
    public static final ClassDef<AnimationTrack> TRACK =
            ClassDef.of("AnimationTrack", null, AnimationTrack.class, AnimationTrack::new);
    public static final ClassDef<Appearance> APPEARANCE =
            ClassDef.of("Appearance", null, Appearance.class, Appearance::new);
    public static final ClassDef<Armour> ARMOUR =
            ClassDef.of("Armour", null, Armour.class, Armour::new);
    public static final ClassDef<Attachment> ATTACHMENT =
            ClassDef.of("Attachment", SPATIAL, Attachment.class, Attachment::new);
    public static final ClassDef<Limb> LIMB = ClassDef.of("Limb", PART, Limb.class, Limb::new);
    public static final ClassDef<Cape> CAPE = ClassDef.of("Cape", LIMB, Cape.class, Cape::new);
    public static final ClassDef<Humanoid> HUMANOID =
            ClassDef.of("Humanoid", null, Humanoid.class, Humanoid::new);
    public static final ClassDef<Wings> WINGS = ClassDef.of("Wings", null, Wings.class, Wings::new);
    // shared state with a name and nowhere else to live
    //
    // five, covering what a game actually keeps: a count, a label, a flag, a place, and a thing
    public static final ClassDef<Value> VALUE = ClassDef.of("Value", null, Value.class, Value::new);
    public static final ClassDef<NumberValue> NUMBER_VALUE =
            ClassDef.of("NumberValue", VALUE, NumberValue.class, NumberValue::new);
    public static final ClassDef<StringValue> STRING_VALUE =
            ClassDef.of("StringValue", VALUE, StringValue.class, StringValue::new);
    public static final ClassDef<BoolValue> BOOL_VALUE =
            ClassDef.of("BoolValue", VALUE, BoolValue.class, BoolValue::new);
    public static final ClassDef<Vector3Value> VECTOR3_VALUE =
            ClassDef.of("Vector3Value", VALUE, Vector3Value.class, Vector3Value::new);
    public static final ClassDef<ObjectValue> OBJECT_VALUE =
            ClassDef.of("ObjectValue", VALUE, ObjectValue.class, ObjectValue::new);

    // the two directions of the boundary, as an instance rather than a registry
    public static final ClassDef<Remote> REMOTE =
            ClassDef.of("Remote", null, Remote.class, Remote::new);
    public static final ClassDef<UnreliableRemote> UNRELIABLE_REMOTE =
            ClassDef.of("UnreliableRemote", REMOTE, UnreliableRemote.class, UnreliableRemote::new);

    public static final ClassDef<ScreenGui> SCREEN_GUI =
            ClassDef.of("ScreenGui", null, ScreenGui.class, ScreenGui::new);
    public static final ClassDef<BillboardGui> BILLBOARD_GUI =
            ClassDef.of("BillboardGui", null, BillboardGui.class, BillboardGui::new);
    public static final ClassDef<SurfaceGui> SURFACE_GUI =
            ClassDef.of("SurfaceGui", null, SurfaceGui.class, SurfaceGui::new);
    public static final ClassDef<GuiObject> GUI_OBJECT =
            ClassDef.of("GuiObject", null, GuiObject.class, GuiObject::new);
    public static final ClassDef<Frame> FRAME = ClassDef.of("Frame", GUI_OBJECT, Frame.class, Frame::new);
    public static final ClassDef<TextLabel> TEXT_LABEL =
            ClassDef.of("TextLabel", GUI_OBJECT, TextLabel.class, TextLabel::new);
    public static final ClassDef<TextButton> TEXT_BUTTON =
            ClassDef.of("TextButton", TEXT_LABEL, TextButton.class, TextButton::new);
    public static final ClassDef<ImageLabel> IMAGE_LABEL =
            ClassDef.of("ImageLabel", GUI_OBJECT, ImageLabel.class, ImageLabel::new);

    public static final ClassDef<Joint> JOINT = ClassDef.of("Joint", null, Joint.class, Joint::new);
    public static final ClassDef<Motor> MOTOR = ClassDef.of("Motor", JOINT, Motor.class, Motor::new);

    private Classes() {}

    public static ClassRegistry registry() {
        return registry(List.of());
    }

    // the engine's own classes, then everyone else's
    //
    // the order matters for nothing on the wire -- an instance crosses as its class name, not an
    // index -- but it matters for a collision: an addon that names a class Part is told so, by an
    // error that names the addon
    public static ClassRegistry registry(Iterable<Addon> addons) {
        ClassRegistry r = new ClassRegistry();
        r.register(FOLDER);
        r.register(SPATIAL);
        r.register(PART);
        r.register(CHARACTER);
        r.register(CAMERA);
        r.register(ANIMATOR);
        r.register(TRACK);
        r.register(APPEARANCE);
        r.register(ARMOUR);
        r.register(ATTACHMENT);
        r.register(LIMB);
        r.register(CAPE);
        r.register(HUMANOID);
        r.register(WINGS);
        r.register(VALUE);
        r.register(NUMBER_VALUE);
        r.register(STRING_VALUE);
        r.register(BOOL_VALUE);
        r.register(VECTOR3_VALUE);
        r.register(OBJECT_VALUE);
        r.register(REMOTE);
        r.register(UNRELIABLE_REMOTE);
        r.register(SCREEN_GUI);
        r.register(BILLBOARD_GUI);
        r.register(SURFACE_GUI);
        r.register(GUI_OBJECT);
        r.register(FRAME);
        r.register(TEXT_LABEL);
        r.register(TEXT_BUTTON);
        r.register(IMAGE_LABEL);
        r.register(JOINT);
        r.register(MOTOR);
        for (Addon addon : addons) {
            try {
                addon.classes(r);
            } catch (RuntimeException failed) {
                throw new IllegalStateException(
                        "addon '" + addon.id() + "' failed to register its classes", failed);
            }
        }
        return r;
    }
}
