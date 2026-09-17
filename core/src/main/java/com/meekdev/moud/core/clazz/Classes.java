package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.core.audio.ChorusSoundEffect;
import com.meekdev.moud.core.audio.CompressorSoundEffect;
import com.meekdev.moud.core.audio.DistortionSoundEffect;
import com.meekdev.moud.core.audio.EchoSoundEffect;
import com.meekdev.moud.core.audio.EqualizerSoundEffect;
import com.meekdev.moud.core.audio.FlangeSoundEffect;
import com.meekdev.moud.core.audio.PitchShiftSoundEffect;
import com.meekdev.moud.core.audio.ReverbSoundEffect;
import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.audio.SoundBus;
import com.meekdev.moud.core.audio.SoundEffect;
import com.meekdev.moud.core.audio.TremoloSoundEffect;
import com.meekdev.moud.core.character.AnimationTrack;
import com.meekdev.moud.core.character.Animator;
import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Armour;
import com.meekdev.moud.core.character.Cape;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Animation;
import com.meekdev.moud.core.character.Backpack;
import com.meekdev.moud.core.character.StarterPack;
import com.meekdev.moud.core.character.Tool;
import com.meekdev.moud.core.character.ForceField;
import com.meekdev.moud.core.character.Keyframe;
import com.meekdev.moud.core.character.KeyframeMarker;
import com.meekdev.moud.core.character.KeyframePose;
import com.meekdev.moud.core.character.KeyframeSequence;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Limb;
import com.meekdev.moud.core.character.Wings;
import com.meekdev.moud.core.chat.BubbleChat;
import com.meekdev.moud.core.chat.ChatCommand;
import com.meekdev.moud.core.chat.ChatInputBar;
import com.meekdev.moud.core.chat.ChatTabs;
import com.meekdev.moud.core.chat.ChatTextShader;
import com.meekdev.moud.core.chat.ChatWindow;
import com.meekdev.moud.core.chat.TextChannel;
import com.meekdev.moud.core.chat.TextSource;
import com.meekdev.moud.core.effect.Beam;
import com.meekdev.moud.core.effect.Decal;
import com.meekdev.moud.core.effect.Explosion;
import com.meekdev.moud.core.effect.Fire;
import com.meekdev.moud.core.effect.Highlight;
import com.meekdev.moud.core.effect.ParticleEmitter;
import com.meekdev.moud.core.effect.SelectionBox;
import com.meekdev.moud.core.effect.SelectionSphere;
import com.meekdev.moud.core.effect.Smoke;
import com.meekdev.moud.core.effect.Sparkles;
import com.meekdev.moud.core.effect.Texture;
import com.meekdev.moud.core.effect.Trail;
import com.meekdev.moud.core.input.ClickDetector;
import com.meekdev.moud.core.input.InputAction;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Folder;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.instance.Motor;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.part.CollisionGroup;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.Seat;
import com.meekdev.moud.core.part.SpawnLocation;
import com.meekdev.moud.core.part.VehicleSeat;
import com.meekdev.moud.core.physics.BallSocketConstraint;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.HingeConstraint;
import com.meekdev.moud.core.physics.PrismaticConstraint;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.SpringConstraint;
import com.meekdev.moud.core.physics.WeldConstraint;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.remote.RemoteFunction;
import com.meekdev.moud.core.remote.UnreliableRemote;
import com.meekdev.moud.core.render.AreaLight;
import com.meekdev.moud.core.render.Atmosphere;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.render.Clouds;
import com.meekdev.moud.core.render.LightSource;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.core.render.PointLight;
import com.meekdev.moud.core.render.Sky;
import com.meekdev.moud.core.render.SpotLight;
import com.meekdev.moud.core.render.TubeLight;
import com.meekdev.moud.core.render.post.AmbientOcclusionEffect;
import com.meekdev.moud.core.render.post.AntiAliasingEffect;
import com.meekdev.moud.core.render.post.BloomEffect;
import com.meekdev.moud.core.render.post.BlurEffect;
import com.meekdev.moud.core.render.post.ChromaticAberrationEffect;
import com.meekdev.moud.core.render.post.ColorGradeEffect;
import com.meekdev.moud.core.render.post.ContactShadowEffect;
import com.meekdev.moud.core.render.post.DepthOfFieldEffect;
import com.meekdev.moud.core.render.post.FilmGrainEffect;
import com.meekdev.moud.core.render.post.FogEffect;
import com.meekdev.moud.core.render.post.GlobalIlluminationEffect;
import com.meekdev.moud.core.render.post.MotionBlurEffect;
import com.meekdev.moud.core.render.post.OutlineEffect;
import com.meekdev.moud.core.render.post.PixelateEffect;
import com.meekdev.moud.core.render.post.PostEffect;
import com.meekdev.moud.core.render.post.PostShader;
import com.meekdev.moud.core.render.post.PosterizeEffect;
import com.meekdev.moud.core.render.post.ReflectionEffect;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.core.render.post.ShadowQuality;
import com.meekdev.moud.core.render.post.SharpenEffect;
import com.meekdev.moud.core.render.post.TonemapEffect;
import com.meekdev.moud.core.render.post.VignetteEffect;
import com.meekdev.moud.core.render.post.VolumetricEffect;
import com.meekdev.moud.core.script.LocalScript;
import com.meekdev.moud.core.script.ModuleScript;
import com.meekdev.moud.core.script.Script;
import com.meekdev.moud.core.player.Leaderboard;
import com.meekdev.moud.core.player.Leaderstats;
import com.meekdev.moud.core.player.Team;
import com.meekdev.moud.core.service.ReplicatedStorage;
import com.meekdev.moud.core.service.ServerScriptService;
import com.meekdev.moud.core.service.ServerStorage;
import com.meekdev.moud.core.service.StarterCharacterScripts;
import com.meekdev.moud.core.service.StarterGui;
import com.meekdev.moud.core.service.StarterPlayerScripts;
import com.meekdev.moud.core.ui.AppWindow;
import com.meekdev.moud.core.ui.BillboardGui;
import com.meekdev.moud.core.ui.CanvasGroup;
import com.meekdev.moud.core.ui.Frame;
import com.meekdev.moud.core.ui.GuiObject;
import com.meekdev.moud.core.ui.ImageButton;
import com.meekdev.moud.core.ui.ImageLabel;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.ScrollingFrame;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.ui.TextBox;
import com.meekdev.moud.core.ui.TextButton;
import com.meekdev.moud.core.ui.TextLabel;
import com.meekdev.moud.core.ui.UIAspectRatioConstraint;
import com.meekdev.moud.core.ui.UIComponent;
import com.meekdev.moud.core.ui.UICorner;
import com.meekdev.moud.core.ui.UIGradient;
import com.meekdev.moud.core.ui.UIGridLayout;
import com.meekdev.moud.core.ui.UILayout;
import com.meekdev.moud.core.ui.UIListLayout;
import com.meekdev.moud.core.ui.UIPadding;
import com.meekdev.moud.core.ui.UIScale;
import com.meekdev.moud.core.ui.UISizeConstraint;
import com.meekdev.moud.core.ui.UIStroke;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.core.value.BoolValue;
import com.meekdev.moud.core.value.NumberValue;
import com.meekdev.moud.core.value.ObjectValue;
import com.meekdev.moud.core.value.StringValue;
import com.meekdev.moud.core.value.Value;
import com.meekdev.moud.core.value.Vector3Value;
import com.meekdev.moud.core.zone.ProximityPrompt;
import com.meekdev.moud.core.zone.Zone;
import java.util.List;

public final class Classes {

    public static final ClassDef<Folder> FOLDER = ClassDef.of("Folder", null, Folder.class, Folder::new);
    public static final ClassDef<Spatial> SPATIAL = ClassDef.of("Spatial", null, Spatial.class, Spatial::new);
    public static final ClassDef<Part> PART = ClassDef.of("Part", SPATIAL, Part.class, Part::new);
    public static final ClassDef<Character> CHARACTER =
            ClassDef.of("Character", SPATIAL, Character.class, Character::new);
    public static final ClassDef<Camera> CAMERA = ClassDef.of("Camera", SPATIAL, Camera.class, Camera::new);
    public static final ClassDef<CameraPath> CAMERA_PATH = ClassDef.of("CameraPath", SPATIAL, CameraPath.class, CameraPath::new);
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
    public static final ClassDef<SpawnLocation> SPAWN_LOCATION = ClassDef.of("SpawnLocation", PART, SpawnLocation.class, SpawnLocation::new);
    public static final ClassDef<Seat> SEAT = ClassDef.of("Seat", PART, Seat.class, Seat::new);
    public static final ClassDef<VehicleSeat> VEHICLE_SEAT =
            ClassDef.of("VehicleSeat", SEAT, VehicleSeat.class, VehicleSeat::new);

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

    public static final ClassDef<ProximityPrompt> PROXIMITY_PROMPT =
            ClassDef.of("ProximityPrompt", null, ProximityPrompt.class, ProximityPrompt::new);
    public static final ClassDef<Zone> ZONE = ClassDef.of("Zone", SPATIAL, Zone.class, Zone::new);

    public static final ClassDef<LightSource> LIGHT = ClassDef.of("Light", SPATIAL, LightSource.class, LightSource::new);
    public static final ClassDef<PointLight> POINT_LIGHT =
            ClassDef.of("PointLight", LIGHT, PointLight.class, PointLight::new);
    public static final ClassDef<SpotLight> SPOT_LIGHT =
            ClassDef.of("SpotLight", LIGHT, SpotLight.class, SpotLight::new);
    public static final ClassDef<AreaLight> AREA_LIGHT =
            ClassDef.of("AreaLight", LIGHT, AreaLight.class, AreaLight::new);
    public static final ClassDef<TubeLight> TUBE_LIGHT =
            ClassDef.of("TubeLight", LIGHT, TubeLight.class, TubeLight::new);

    public static final ClassDef<Lighting> LIGHTING = ClassDef.of("Lighting", null, Lighting.class, Lighting::new);
    public static final ClassDef<Sky> SKY = ClassDef.of("Sky", null, Sky.class, Sky::new);
    public static final ClassDef<Atmosphere> ATMOSPHERE =
            ClassDef.of("Atmosphere", null, Atmosphere.class, Atmosphere::new);
    public static final ClassDef<Clouds> CLOUDS = ClassDef.of("Clouds", null, Clouds.class, Clouds::new);
    public static final ClassDef<Cape> CAPE = ClassDef.of("Cape", LIMB, Cape.class, Cape::new);
    public static final ClassDef<Humanoid> HUMANOID =
            ClassDef.of("Humanoid", null, Humanoid.class, Humanoid::new);
    public static final ClassDef<Wings> WINGS = ClassDef.of("Wings", null, Wings.class, Wings::new);
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

    public static final ClassDef<Remote> REMOTE =
            ClassDef.of("Remote", null, Remote.class, Remote::new);
    public static final ClassDef<UnreliableRemote> UNRELIABLE_REMOTE =
            ClassDef.of("UnreliableRemote", REMOTE, UnreliableRemote.class, UnreliableRemote::new);
    public static final ClassDef<RemoteFunction> REMOTE_FUNCTION =
            ClassDef.of("RemoteFunction", null, RemoteFunction.class, RemoteFunction::new);

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

    public static final ClassDef<ImageButton> IMAGE_BUTTON =
            ClassDef.of("ImageButton", IMAGE_LABEL, ImageButton.class, ImageButton::new);
    public static final ClassDef<TextBox> TEXT_BOX = ClassDef.of("TextBox", TEXT_LABEL, TextBox.class, TextBox::new);
    public static final ClassDef<ScrollingFrame> SCROLLING_FRAME =
            ClassDef.of("ScrollingFrame", GUI_OBJECT, ScrollingFrame.class, ScrollingFrame::new);
    public static final ClassDef<CanvasGroup> CANVAS_GROUP =
            ClassDef.of("CanvasGroup", GUI_OBJECT, CanvasGroup.class, CanvasGroup::new);
    public static final ClassDef<UIComponent> UI_COMPONENT =
            ClassDef.of("UIComponent", null, UIComponent.class, UIComponent::new);
    public static final ClassDef<UILayout> UI_LAYOUT = ClassDef.of("UILayout", UI_COMPONENT, UILayout.class, UILayout::new);
    public static final ClassDef<UIListLayout> UI_LIST_LAYOUT =
            ClassDef.of("UIListLayout", UI_LAYOUT, UIListLayout.class, UIListLayout::new);
    public static final ClassDef<UIGridLayout> UI_GRID_LAYOUT =
            ClassDef.of("UIGridLayout", UI_LAYOUT, UIGridLayout.class, UIGridLayout::new);
    public static final ClassDef<UIPadding> UI_PADDING = ClassDef.of("UIPadding", UI_COMPONENT, UIPadding.class, UIPadding::new);
    public static final ClassDef<UIAspectRatioConstraint> UI_ASPECT_RATIO_CONSTRAINT =
            ClassDef.of("UIAspectRatioConstraint", UI_COMPONENT, UIAspectRatioConstraint.class, UIAspectRatioConstraint::new);
    public static final ClassDef<UISizeConstraint> UI_SIZE_CONSTRAINT =
            ClassDef.of("UISizeConstraint", UI_COMPONENT, UISizeConstraint.class, UISizeConstraint::new);
    public static final ClassDef<UIScale> UI_SCALE = ClassDef.of("UIScale", UI_COMPONENT, UIScale.class, UIScale::new);
    public static final ClassDef<UIStroke> UI_STROKE = ClassDef.of("UIStroke", UI_COMPONENT, UIStroke.class, UIStroke::new);
    public static final ClassDef<UIGradient> UI_GRADIENT =
            ClassDef.of("UIGradient", UI_COMPONENT, UIGradient.class, UIGradient::new);
    public static final ClassDef<UICorner> UI_CORNER = ClassDef.of("UICorner", UI_COMPONENT, UICorner.class, UICorner::new);

    public static final ClassDef<ViewportFrame> VIEWPORT_FRAME =
            ClassDef.of("ViewportFrame", GUI_OBJECT, ViewportFrame.class, ViewportFrame::new);
    public static final ClassDef<AppWindow> WINDOW = ClassDef.of("Window", null, AppWindow.class, AppWindow::new);

    public static final ClassDef<Sound> SOUND = ClassDef.of("Sound", null, Sound.class, Sound::new);
    public static final ClassDef<SoundBus> SOUND_BUS =
            ClassDef.of("SoundBus", null, SoundBus.class, SoundBus::new);
    public static final ClassDef<SoundEffect> SOUND_EFFECT =
            ClassDef.of("SoundEffect", null, SoundEffect.class, SoundEffect::new);
    public static final ClassDef<ReverbSoundEffect> REVERB_SOUND_EFFECT =
            ClassDef.of("ReverbSoundEffect", SOUND_EFFECT, ReverbSoundEffect.class, ReverbSoundEffect::new);
    public static final ClassDef<EqualizerSoundEffect> EQUALIZER_SOUND_EFFECT =
            ClassDef.of("EqualizerSoundEffect", SOUND_EFFECT, EqualizerSoundEffect.class, EqualizerSoundEffect::new);
    public static final ClassDef<DistortionSoundEffect> DISTORTION_SOUND_EFFECT =
            ClassDef.of("DistortionSoundEffect", SOUND_EFFECT, DistortionSoundEffect.class, DistortionSoundEffect::new);
    public static final ClassDef<EchoSoundEffect> ECHO_SOUND_EFFECT =
            ClassDef.of("EchoSoundEffect", SOUND_EFFECT, EchoSoundEffect.class, EchoSoundEffect::new);
    public static final ClassDef<PitchShiftSoundEffect> PITCH_SHIFT_SOUND_EFFECT =
            ClassDef.of("PitchShiftSoundEffect", SOUND_EFFECT, PitchShiftSoundEffect.class, PitchShiftSoundEffect::new);
    public static final ClassDef<CompressorSoundEffect> COMPRESSOR_SOUND_EFFECT =
            ClassDef.of("CompressorSoundEffect", SOUND_EFFECT, CompressorSoundEffect.class, CompressorSoundEffect::new);
    public static final ClassDef<ChorusSoundEffect> CHORUS_SOUND_EFFECT =
            ClassDef.of("ChorusSoundEffect", SOUND_EFFECT, ChorusSoundEffect.class, ChorusSoundEffect::new);
    public static final ClassDef<FlangeSoundEffect> FLANGE_SOUND_EFFECT =
            ClassDef.of("FlangeSoundEffect", SOUND_EFFECT, FlangeSoundEffect.class, FlangeSoundEffect::new);
    public static final ClassDef<TremoloSoundEffect> TREMOLO_SOUND_EFFECT =
            ClassDef.of("TremoloSoundEffect", SOUND_EFFECT, TremoloSoundEffect.class, TremoloSoundEffect::new);

    public static final ClassDef<Script> SCRIPT = ClassDef.of("Script", null, Script.class, Script::new);
    public static final ClassDef<LocalScript> LOCAL_SCRIPT =
            ClassDef.of("LocalScript", null, LocalScript.class, LocalScript::new);
    public static final ClassDef<ModuleScript> MODULE_SCRIPT =
            ClassDef.of("ModuleScript", null, ModuleScript.class, ModuleScript::new);

    public static final ClassDef<InputAction> INPUT_ACTION =
            ClassDef.of("InputAction", null, InputAction.class, InputAction::new);
    public static final ClassDef<ClickDetector> CLICK_DETECTOR =
            ClassDef.of("ClickDetector", null, ClickDetector.class, ClickDetector::new);

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
    public static final ClassDef<Model> MODEL = ClassDef.of("Model", SPATIAL, Model.class, Model::new);
    public static final ClassDef<ForceField> FORCE_FIELD = ClassDef.of("ForceField", null, ForceField.class, ForceField::new);
    public static final ClassDef<Tool> TOOL = ClassDef.of("Tool", null, Tool.class, Tool::new);
    public static final ClassDef<Team> TEAM = ClassDef.of("Team", null, Team.class, Team::new);
    public static final ClassDef<Leaderboard> LEADERBOARD =
            ClassDef.of("Leaderboard", null, Leaderboard.class, Leaderboard::new);
    public static final ClassDef<Leaderstats> LEADERSTATS =
            ClassDef.of("Leaderstats", null, Leaderstats.class, Leaderstats::new);
    public static final ClassDef<ReplicatedStorage> REPLICATED_STORAGE = ClassDef.of("ReplicatedStorage", null, ReplicatedStorage.class, ReplicatedStorage::new);
    public static final ClassDef<ServerStorage> SERVER_STORAGE = ClassDef.of("ServerStorage", null, ServerStorage.class, ServerStorage::new);
    public static final ClassDef<ServerScriptService> SERVER_SCRIPT_SERVICE = ClassDef.of("ServerScriptService", null, ServerScriptService.class, ServerScriptService::new);
    public static final ClassDef<StarterGui> STARTER_GUI = ClassDef.of("StarterGui", null, StarterGui.class, StarterGui::new);
    public static final ClassDef<StarterPlayerScripts> STARTER_PLAYER_SCRIPTS = ClassDef.of("StarterPlayerScripts", null, StarterPlayerScripts.class, StarterPlayerScripts::new);
    public static final ClassDef<StarterCharacterScripts> STARTER_CHARACTER_SCRIPTS = ClassDef.of("StarterCharacterScripts", null, StarterCharacterScripts.class, StarterCharacterScripts::new);
    public static final ClassDef<Backpack> BACKPACK = ClassDef.of("Backpack", null, Backpack.class, Backpack::new);
    public static final ClassDef<StarterPack> STARTER_PACK = ClassDef.of("StarterPack", null, StarterPack.class, StarterPack::new);
    public static final ClassDef<Animation> ANIMATION = ClassDef.of("Animation", null, Animation.class, Animation::new);
    public static final ClassDef<KeyframeSequence> KEYFRAME_SEQUENCE =
            ClassDef.of("KeyframeSequence", null, KeyframeSequence.class, KeyframeSequence::new);
    public static final ClassDef<Keyframe> KEYFRAME = ClassDef.of("Keyframe", null, Keyframe.class, Keyframe::new);
    public static final ClassDef<KeyframePose> POSE = ClassDef.of("Pose", null, KeyframePose.class, KeyframePose::new);
    public static final ClassDef<KeyframeMarker> KEYFRAME_MARKER = ClassDef.of("KeyframeMarker", null, KeyframeMarker.class, KeyframeMarker::new);
    public static final ClassDef<WeldConstraint> WELD_CONSTRAINT =
            ClassDef.of("WeldConstraint", null, WeldConstraint.class, WeldConstraint::new);
    public static final ClassDef<Constraint> CONSTRAINT = ClassDef.of("Constraint", null, Constraint.class, Constraint::new);
    public static final ClassDef<HingeConstraint> HINGE_CONSTRAINT =
            ClassDef.of("HingeConstraint", CONSTRAINT, HingeConstraint.class, HingeConstraint::new);
    public static final ClassDef<PrismaticConstraint> PRISMATIC_CONSTRAINT =
            ClassDef.of("PrismaticConstraint", CONSTRAINT, PrismaticConstraint.class, PrismaticConstraint::new);
    public static final ClassDef<BallSocketConstraint> BALL_SOCKET_CONSTRAINT =
            ClassDef.of("BallSocketConstraint", CONSTRAINT, BallSocketConstraint.class, BallSocketConstraint::new);
    public static final ClassDef<RopeConstraint> ROPE_CONSTRAINT =
            ClassDef.of("RopeConstraint", CONSTRAINT, RopeConstraint.class, RopeConstraint::new);
    public static final ClassDef<SpringConstraint> SPRING_CONSTRAINT =
            ClassDef.of("SpringConstraint", CONSTRAINT, SpringConstraint.class, SpringConstraint::new);

    public static final ClassDef<ParticleEmitter> PARTICLE_EMITTER =
            ClassDef.of("ParticleEmitter", null, ParticleEmitter.class, ParticleEmitter::new);
    public static final ClassDef<Beam> BEAM = ClassDef.of("Beam", null, Beam.class, Beam::new);
    public static final ClassDef<Trail> TRAIL = ClassDef.of("Trail", null, Trail.class, Trail::new);
    public static final ClassDef<Highlight> HIGHLIGHT = ClassDef.of("Highlight", null, Highlight.class, Highlight::new);
    public static final ClassDef<Decal> DECAL = ClassDef.of("Decal", null, Decal.class, Decal::new);
    public static final ClassDef<Explosion> EXPLOSION = ClassDef.of("Explosion", null, Explosion.class, Explosion::new);
    public static final ClassDef<Texture> TEXTURE = ClassDef.of("Texture", DECAL, Texture.class, Texture::new);
    public static final ClassDef<Fire> FIRE = ClassDef.of("Fire", null, Fire.class, Fire::new);
    public static final ClassDef<Smoke> SMOKE = ClassDef.of("Smoke", null, Smoke.class, Smoke::new);
    public static final ClassDef<Sparkles> SPARKLES = ClassDef.of("Sparkles", null, Sparkles.class, Sparkles::new);
    public static final ClassDef<SelectionBox> SELECTION_BOX =
            ClassDef.of("SelectionBox", null, SelectionBox.class, SelectionBox::new);
    public static final ClassDef<SelectionSphere> SELECTION_SPHERE =
            ClassDef.of("SelectionSphere", null, SelectionSphere.class, SelectionSphere::new);

    private Classes() {}

    public static ClassRegistry registry() {
        return registry(List.of());
    }

    public static ClassRegistry registry(Iterable<Addon> addons) {
        ClassRegistry r = new ClassRegistry();
        r.register(FOLDER);
        r.register(SPATIAL);
        r.register(PART);
        r.register(CHARACTER);
        r.register(CAMERA);
        r.register(CAMERA_PATH);
        r.register(ANIMATOR);
        r.register(TRACK);
        r.register(APPEARANCE);
        r.register(ARMOUR);
        r.register(ATTACHMENT);
        r.register(LIMB);
        r.register(MESH_PART);
        r.register(SPAWN_LOCATION);
        r.register(SEAT);
        r.register(VEHICLE_SEAT);
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
        r.register(ZONE);
        r.register(PROXIMITY_PROMPT);
        r.register(LIGHT);
        r.register(POINT_LIGHT);
        r.register(SPOT_LIGHT);
        r.register(AREA_LIGHT);
        r.register(TUBE_LIGHT);
        r.register(LIGHTING);
        r.register(SKY);
        r.register(ATMOSPHERE);
        r.register(CLOUDS);
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
        r.register(REMOTE_FUNCTION);
        r.register(SCREEN_GUI);
        r.register(BILLBOARD_GUI);
        r.register(SURFACE_GUI);
        r.register(GUI_OBJECT);
        r.register(FRAME);
        r.register(TEXT_LABEL);
        r.register(TEXT_BUTTON);
        r.register(IMAGE_LABEL);
        r.register(IMAGE_BUTTON);
        r.register(TEXT_BOX);
        r.register(SCROLLING_FRAME);
        r.register(CANVAS_GROUP);
        r.register(UI_COMPONENT);
        r.register(UI_LAYOUT);
        r.register(UI_LIST_LAYOUT);
        r.register(UI_GRID_LAYOUT);
        r.register(UI_PADDING);
        r.register(UI_ASPECT_RATIO_CONSTRAINT);
        r.register(UI_SIZE_CONSTRAINT);
        r.register(UI_SCALE);
        r.register(UI_STROKE);
        r.register(UI_GRADIENT);
        r.register(UI_CORNER);
        r.register(VIEWPORT_FRAME);
        r.register(WINDOW);
        r.register(SOUND);
        r.register(SOUND_BUS);
        r.register(SOUND_EFFECT);
        r.register(REVERB_SOUND_EFFECT);
        r.register(EQUALIZER_SOUND_EFFECT);
        r.register(DISTORTION_SOUND_EFFECT);
        r.register(ECHO_SOUND_EFFECT);
        r.register(PITCH_SHIFT_SOUND_EFFECT);
        r.register(COMPRESSOR_SOUND_EFFECT);
        r.register(CHORUS_SOUND_EFFECT);
        r.register(FLANGE_SOUND_EFFECT);
        r.register(TREMOLO_SOUND_EFFECT);
        r.register(SCRIPT);
        r.register(LOCAL_SCRIPT);
        r.register(MODULE_SCRIPT);
        r.register(INPUT_ACTION);
        r.register(CLICK_DETECTOR);
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
        r.register(MODEL);
        r.register(FORCE_FIELD);
        r.register(TOOL);
        r.register(TEAM);
        r.register(LEADERBOARD);
        r.register(LEADERSTATS);
        r.register(REPLICATED_STORAGE);
        r.register(SERVER_STORAGE);
        r.register(SERVER_SCRIPT_SERVICE);
        r.register(STARTER_GUI);
        r.register(STARTER_PLAYER_SCRIPTS);
        r.register(STARTER_CHARACTER_SCRIPTS);
        r.register(BACKPACK);
        r.register(STARTER_PACK);
        r.register(ANIMATION);
        r.register(KEYFRAME_SEQUENCE);
        r.register(KEYFRAME);
        r.register(POSE);
        r.register(KEYFRAME_MARKER);
        r.register(WELD_CONSTRAINT);
        r.register(CONSTRAINT);
        r.register(HINGE_CONSTRAINT);
        r.register(PRISMATIC_CONSTRAINT);
        r.register(BALL_SOCKET_CONSTRAINT);
        r.register(ROPE_CONSTRAINT);
        r.register(SPRING_CONSTRAINT);
        r.register(PARTICLE_EMITTER);
        r.register(BEAM);
        r.register(TRAIL);
        r.register(HIGHLIGHT);
        r.register(DECAL);
        r.register(EXPLOSION);
        r.register(TEXTURE);
        r.register(FIRE);
        r.register(SMOKE);
        r.register(SPARKLES);
        r.register(SELECTION_BOX);
        r.register(SELECTION_SPHERE);
        for (Addon addon : addons) {
            try {
                addon.classes(r);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "addon '" + addon.id() + "' e to register its classes", e);
            }
        }
        return r;
    }
}
