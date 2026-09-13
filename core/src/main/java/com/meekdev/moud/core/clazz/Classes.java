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
import com.meekdev.moud.core.instance.BubbleChat;
import com.meekdev.moud.core.instance.ChatCommand;
import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.core.instance.TextChannel;
import com.meekdev.moud.core.instance.TextSource;
import com.meekdev.moud.core.instance.ChatInputBar;
import com.meekdev.moud.core.instance.ChatTabs;
import com.meekdev.moud.core.instance.ChatTextShader;
import com.meekdev.moud.core.instance.CollisionGroup;
import com.meekdev.moud.core.instance.Folder;
import com.meekdev.moud.core.instance.Frame;
import com.meekdev.moud.core.instance.GuiObject;
import com.meekdev.moud.core.instance.Humanoid;
import com.meekdev.moud.core.instance.ImageLabel;
import com.meekdev.moud.core.instance.InputAction;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Limb;
import com.meekdev.moud.core.instance.MeshPart;
import com.meekdev.moud.core.instance.AreaLight;
import com.meekdev.moud.core.instance.PostEffect;
import com.meekdev.moud.core.instance.ScreenEffect;
import com.meekdev.moud.core.instance.BloomEffect;
import com.meekdev.moud.core.instance.ColorGradeEffect;
import com.meekdev.moud.core.instance.AmbientOcclusionEffect;
import com.meekdev.moud.core.instance.GlobalIlluminationEffect;
import com.meekdev.moud.core.instance.ReflectionEffect;
import com.meekdev.moud.core.instance.AntiAliasingEffect;
import com.meekdev.moud.core.instance.VolumetricEffect;
import com.meekdev.moud.core.instance.ContactShadowEffect;
import com.meekdev.moud.core.instance.ShadowQuality;
import com.meekdev.moud.core.instance.VignetteEffect;
import com.meekdev.moud.core.instance.ChromaticAberrationEffect;
import com.meekdev.moud.core.instance.FilmGrainEffect;
import com.meekdev.moud.core.instance.BlurEffect;
import com.meekdev.moud.core.instance.DepthOfFieldEffect;
import com.meekdev.moud.core.instance.MotionBlurEffect;
import com.meekdev.moud.core.instance.PixelateEffect;
import com.meekdev.moud.core.instance.PosterizeEffect;
import com.meekdev.moud.core.instance.SharpenEffect;
import com.meekdev.moud.core.instance.FogEffect;
import com.meekdev.moud.core.instance.OutlineEffect;
import com.meekdev.moud.core.instance.TonemapEffect;
import com.meekdev.moud.core.instance.PostShader;
import com.meekdev.moud.core.instance.Light;
import com.meekdev.moud.core.instance.PointLight;
import com.meekdev.moud.core.instance.SpotLight;
import com.meekdev.moud.core.instance.TubeLight;
import com.meekdev.moud.core.instance.LocalScript;
import com.meekdev.moud.core.instance.Motor;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.ScreenGui;
import com.meekdev.moud.core.instance.Script;
import com.meekdev.moud.core.instance.Sound;
import com.meekdev.moud.core.instance.SoundBus;
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
    public static final ClassDef<MeshPart> MESH_PART = ClassDef.of("MeshPart", PART, MeshPart.class, MeshPart::new);

    public static final ClassDef<PostEffect> POST_EFFECT = ClassDef.of("PostEffect", null, PostEffect.class, PostEffect::new);
    public static final ClassDef<ScreenEffect> SCREEN_EFFECT = ClassDef.of("ScreenEffect", POST_EFFECT, ScreenEffect.class, ScreenEffect::new);
    public static final ClassDef<BloomEffect> BLOOM_EFFECT =
            ClassDef.of("BloomEffect", POST_EFFECT, BloomEffect.class, BloomEffect::new);
    public static final ClassDef<ColorGradeEffect> COLOR_GRADE_EFFECT =
            ClassDef.of("ColorGradeEffect", POST_EFFECT, ColorGradeEffect.class, ColorGradeEffect::new);
    public static final ClassDef<AmbientOcclusionEffect> AMBIENT_OCCLUSION_EFFECT =
            ClassDef.of("AmbientOcclusionEffect", POST_EFFECT, AmbientOcclusionEffect.class, AmbientOcclusionEffect::new);
    public static final ClassDef<GlobalIlluminationEffect> GLOBAL_ILLUMINATION_EFFECT =
            ClassDef.of("GlobalIlluminationEffect", POST_EFFECT, GlobalIlluminationEffect.class, GlobalIlluminationEffect::new);
    public static final ClassDef<ReflectionEffect> REFLECTION_EFFECT =
            ClassDef.of("ReflectionEffect", POST_EFFECT, ReflectionEffect.class, ReflectionEffect::new);
    public static final ClassDef<AntiAliasingEffect> ANTI_ALIASING_EFFECT =
            ClassDef.of("AntiAliasingEffect", POST_EFFECT, AntiAliasingEffect.class, AntiAliasingEffect::new);
    public static final ClassDef<VolumetricEffect> VOLUMETRIC_EFFECT =
            ClassDef.of("VolumetricEffect", POST_EFFECT, VolumetricEffect.class, VolumetricEffect::new);
    public static final ClassDef<ContactShadowEffect> CONTACT_SHADOW_EFFECT =
            ClassDef.of("ContactShadowEffect", POST_EFFECT, ContactShadowEffect.class, ContactShadowEffect::new);
    public static final ClassDef<ShadowQuality> SHADOW_QUALITY =
            ClassDef.of("ShadowQuality", POST_EFFECT, ShadowQuality.class, ShadowQuality::new);
    public static final ClassDef<VignetteEffect> VIGNETTE_EFFECT =
            ClassDef.of("VignetteEffect", SCREEN_EFFECT, VignetteEffect.class, VignetteEffect::new);
    public static final ClassDef<ChromaticAberrationEffect> CHROMATIC_ABERRATION_EFFECT =
            ClassDef.of("ChromaticAberrationEffect", SCREEN_EFFECT, ChromaticAberrationEffect.class, ChromaticAberrationEffect::new);
    public static final ClassDef<FilmGrainEffect> FILM_GRAIN_EFFECT =
            ClassDef.of("FilmGrainEffect", SCREEN_EFFECT, FilmGrainEffect.class, FilmGrainEffect::new);
    public static final ClassDef<BlurEffect> BLUR_EFFECT =
            ClassDef.of("BlurEffect", SCREEN_EFFECT, BlurEffect.class, BlurEffect::new);
    public static final ClassDef<DepthOfFieldEffect> DEPTH_OF_FIELD_EFFECT =
            ClassDef.of("DepthOfFieldEffect", SCREEN_EFFECT, DepthOfFieldEffect.class, DepthOfFieldEffect::new);
    public static final ClassDef<MotionBlurEffect> MOTION_BLUR_EFFECT =
            ClassDef.of("MotionBlurEffect", SCREEN_EFFECT, MotionBlurEffect.class, MotionBlurEffect::new);
    public static final ClassDef<PixelateEffect> PIXELATE_EFFECT =
            ClassDef.of("PixelateEffect", SCREEN_EFFECT, PixelateEffect.class, PixelateEffect::new);
    public static final ClassDef<PosterizeEffect> POSTERIZE_EFFECT =
            ClassDef.of("PosterizeEffect", SCREEN_EFFECT, PosterizeEffect.class, PosterizeEffect::new);
    public static final ClassDef<SharpenEffect> SHARPEN_EFFECT =
            ClassDef.of("SharpenEffect", SCREEN_EFFECT, SharpenEffect.class, SharpenEffect::new);
    public static final ClassDef<FogEffect> FOG_EFFECT =
            ClassDef.of("FogEffect", SCREEN_EFFECT, FogEffect.class, FogEffect::new);
    public static final ClassDef<OutlineEffect> OUTLINE_EFFECT =
            ClassDef.of("OutlineEffect", SCREEN_EFFECT, OutlineEffect.class, OutlineEffect::new);
    public static final ClassDef<TonemapEffect> TONEMAP_EFFECT =
            ClassDef.of("TonemapEffect", SCREEN_EFFECT, TonemapEffect.class, TonemapEffect::new);
    public static final ClassDef<PostShader> POST_SHADER =
            ClassDef.of("PostShader", SCREEN_EFFECT, PostShader.class, PostShader::new);

    public static final ClassDef<Light> LIGHT = ClassDef.of("Light", SPATIAL, Light.class, Light::new);
    public static final ClassDef<PointLight> POINT_LIGHT =
            ClassDef.of("PointLight", LIGHT, PointLight.class, PointLight::new);
    public static final ClassDef<SpotLight> SPOT_LIGHT =
            ClassDef.of("SpotLight", LIGHT, SpotLight.class, SpotLight::new);
    public static final ClassDef<AreaLight> AREA_LIGHT =
            ClassDef.of("AreaLight", LIGHT, AreaLight.class, AreaLight::new);
    public static final ClassDef<TubeLight> TUBE_LIGHT =
            ClassDef.of("TubeLight", LIGHT, TubeLight.class, TubeLight::new);
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

    public static final ClassDef<Sound> SOUND = ClassDef.of("Sound", null, Sound.class, Sound::new);
    public static final ClassDef<SoundBus> SOUND_BUS =
            ClassDef.of("SoundBus", null, SoundBus.class, SoundBus::new);

    public static final ClassDef<Script> SCRIPT = ClassDef.of("Script", null, Script.class, Script::new);
    public static final ClassDef<LocalScript> LOCAL_SCRIPT =
            ClassDef.of("LocalScript", null, LocalScript.class, LocalScript::new);

    public static final ClassDef<InputAction> INPUT_ACTION =
            ClassDef.of("InputAction", null, InputAction.class, InputAction::new);

    public static final ClassDef<ChatWindow> CHAT_WINDOW =
            ClassDef.of("ChatWindow", null, ChatWindow.class, ChatWindow::new);
    public static final ClassDef<TextChannel> TEXT_CHANNEL =
            ClassDef.of("TextChannel", null, TextChannel.class, TextChannel::new);
    public static final ClassDef<TextSource> TEXT_SOURCE =
            ClassDef.of("TextSource", null, TextSource.class, TextSource::new);
    public static final ClassDef<ChatInputBar> CHAT_INPUT_BAR =
            ClassDef.of("ChatInputBar", null, ChatInputBar.class, ChatInputBar::new);
    public static final ClassDef<ChatTabs> CHAT_TABS =
            ClassDef.of("ChatTabs", null, ChatTabs.class, ChatTabs::new);
    public static final ClassDef<ChatTextShader> CHAT_TEXT_SHADER =
            ClassDef.of("ChatTextShader", null, ChatTextShader.class, ChatTextShader::new);
    public static final ClassDef<BubbleChat> BUBBLE_CHAT =
            ClassDef.of("BubbleChat", null, BubbleChat.class, BubbleChat::new);
    public static final ClassDef<ChatCommand> CHAT_COMMAND =
            ClassDef.of("ChatCommand", null, ChatCommand.class, ChatCommand::new);

    public static final ClassDef<CollisionGroup> COLLISION_GROUP =
            ClassDef.of("CollisionGroup", null, CollisionGroup.class, CollisionGroup::new);

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
        r.register(MESH_PART);
        r.register(POST_EFFECT);
        r.register(SCREEN_EFFECT);
        r.register(BLOOM_EFFECT);
        r.register(COLOR_GRADE_EFFECT);
        r.register(AMBIENT_OCCLUSION_EFFECT);
        r.register(GLOBAL_ILLUMINATION_EFFECT);
        r.register(REFLECTION_EFFECT);
        r.register(ANTI_ALIASING_EFFECT);
        r.register(VOLUMETRIC_EFFECT);
        r.register(CONTACT_SHADOW_EFFECT);
        r.register(SHADOW_QUALITY);
        r.register(VIGNETTE_EFFECT);
        r.register(CHROMATIC_ABERRATION_EFFECT);
        r.register(FILM_GRAIN_EFFECT);
        r.register(BLUR_EFFECT);
        r.register(DEPTH_OF_FIELD_EFFECT);
        r.register(MOTION_BLUR_EFFECT);
        r.register(PIXELATE_EFFECT);
        r.register(POSTERIZE_EFFECT);
        r.register(SHARPEN_EFFECT);
        r.register(FOG_EFFECT);
        r.register(OUTLINE_EFFECT);
        r.register(TONEMAP_EFFECT);
        r.register(POST_SHADER);
        r.register(LIGHT);
        r.register(POINT_LIGHT);
        r.register(SPOT_LIGHT);
        r.register(AREA_LIGHT);
        r.register(TUBE_LIGHT);
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
        r.register(SOUND);
        r.register(SOUND_BUS);
        r.register(SCRIPT);
        r.register(LOCAL_SCRIPT);
        r.register(INPUT_ACTION);
        r.register(COLLISION_GROUP);
        r.register(CHAT_WINDOW);
        r.register(CHAT_COMMAND);
        r.register(BUBBLE_CHAT);
        r.register(TEXT_CHANNEL);
        r.register(TEXT_SOURCE);
        r.register(CHAT_INPUT_BAR);
        r.register(CHAT_TABS);
        r.register(CHAT_TEXT_SHADER);
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
