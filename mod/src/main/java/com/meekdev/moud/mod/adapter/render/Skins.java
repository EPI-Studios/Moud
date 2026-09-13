package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Armour;
import com.meekdev.moud.core.instance.Appearance;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.CameraMode;
import com.meekdev.moud.core.instance.FirstPerson;
import com.meekdev.moud.core.instance.Cape;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.CharacterDisplay;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Limb;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Wings;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.features.Feature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector4f;

// a body wearing the player's own skin
//
// one batch per skin, because a batch has one texture and two players rarely share one. the
// batches are made as skins turn up and kept, since a place has as many as it has players
public final class Skins {

    private static final InstanceLayout LAYOUT =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).vec4(7).vec2(8).vec2(9).vec4(10).build();

    private record Worn(Matrix4f transform, Vector4f color, Vector2f light,
                        Vector4f uv, Vector2f box, Vector2f overlay, Vector4f sheet) {}

    private static final Map<Identifier, Identifier> BATCHES = new HashMap<>();
    private static final Map<Identifier, List<Worn>> PACKED = new HashMap<>();

    // one body's wash, set once per character and read by each of its twelve parts
    private static final Vector2f OVERLAY = new Vector2f();

    private static final Identifier RIPTIDE =
            Identifier.withDefaultNamespace("textures/entity/trident/trident_riptide.png");

    private static final Identifier ELYTRA =
            Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();

    private static int drawn;

    private Skins() {}

    public static int count() {
        return drawn;
    }

    // a rig part is drawn here and must not also be drawn flat by the ordinary batches, or the
    // body is painted twice and the skin loses to whichever went second
    // a rig part is drawn here and must not also be drawn flat by the ordinary batches, or the body
    // is painted twice and whichever wrote depth second wins
    //
    // it used to answer by looking the part's name up in a table of six, so a cape, a wing, a plate
    // of armour, a worn head and a pair of ears all answered no and were drawn white underneath
    // their own proper selves. a limb answers for itself
    public static boolean wearsSkin(Part part) {
        return part instanceof Limb;
    }

    public static void gather(float partialTick) {
        for (List<Worn> packed : PACKED.values()) packed.clear();
        drawn = 0;

        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Character mine = ClientScene.own();

        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            Appearance look = Rig.appearance(character);
            if (look == null || look.display != CharacterDisplay.MODEL) continue;
            // your own body, with the camera inside its head. the game's answer is to draw none of
            // it and hold up a hand instead; a place that asked for the whole body gets the whole
            // body, and sees nothing of the head because its faces point away from the inside
            if (character == mine && inside() && look.firstPerson != FirstPerson.BODY) continue;

            AbstractClientPlayer wearer = wearerOf(character);
            Identifier body = bodySheet(look, wearer);

            // an invisible body is not drawn at all to anyone it is invisible to, and drawn at a
            // sixth of solid to anyone it is not -- which includes yourself, so going invisible
            // leaves you a ghost of your own body rather than nothing
            float solid = 1f;
            if (wearer != null && wearer.isInvisible()) {
                LocalPlayer me = Minecraft.getInstance().player;
                if (me == null || wearer.isInvisibleTo(me)) continue;
                solid = 39f / 255f;
            }
            OVERLAY.set((float) character.whiteFlash, character.hurt ? 1f : 0f);
            walk(character, character, wearer, body, solid, partialTick);
        }
    }

    // every limb under a body, however deep and whatever a place added
    //
    // depth first rather than a list of names: a cape hangs off a torso, a plate off the limb it
    // covers, an ear off the point a hat is worn at, and a seventh limb a place hung on somewhere
    // nobody planned for is drawn like all of them
    private static void walk(Character character, Instance under,
                             @Nullable AbstractClientPlayer wearer, Identifier body,
                             float solid, float partialTick) {
        for (Instance child : under.children()) {
            if (child instanceof Limb limb && limb.visible && shows(wearer, limb)) {
                Identifier sheet = sheetOf(limb, character, wearer, body);
                if (sheet != null) {
                    List<Worn> into = PACKED.computeIfAbsent(sheet, id -> {
                        register(id);
                        return new ArrayList<>();
                    });
                    emit(character, limb, solid, into, partialTick);
                }
            }
            // a limb that is switched off still holds up what hangs on it, so this does not stop
            if (child instanceof Spatial) walk(character, child, wearer, body, solid, partialTick);
        }
    }

    // which png, out of what the limb says and what the player is wearing
    //
    // the limb names a role rather than a path, because which png a cape or a helmet is depends on
    // what is worn, and that is a thing this client can see and the engine cannot. a place that
    // wants a particular png writes the path instead and gets it
    private static @Nullable Identifier sheetOf(Limb limb, Character character,
                                                @Nullable AbstractClientPlayer wearer,
                                                Identifier body) {
        String asked = limb.sheet;
        if (asked.isEmpty()) return body;
        switch (asked) {
            case Rig.SHEET_RIPTIDE -> {
                return RIPTIDE;
            }
            case Rig.SHEET_CAPE -> {
                // a cape nobody has is nothing rather than a default one: the game draws no cape on
                // a player without one, and a place that wants one writes a path on the cape itself
                if (wearer != null && wearer.getSkin().cape() != null) {
                    return wearer.getSkin().cape().texturePath();
                }
                return null;
            }
            case Rig.SHEET_ELYTRA -> {
                Wings pair = Rig.wings(character);
                if (pair != null && !pair.skin.isEmpty()) return Identifier.tryParse(pair.skin);
                if (wearer != null) {
                    PlayerSkin skin = wearer.getSkin();
                    if (skin.elytra() != null) return skin.elytra().texturePath();
                    if (skin.cape() != null) return skin.cape().texturePath();
                }
                // an elytra is a real item and always has a look
                return ELYTRA;
            }
            default -> { }
        }
        if (asked.startsWith(Rig.SHEET_ARMOUR)) {
            Armour worn = Rig.armour(character);
            if (worn == null) return null;
            String sheet = Rig.slot(worn, asked.substring(Rig.SHEET_ARMOUR.length()));
            return sheet.isEmpty() ? null : Identifier.tryParse(sheet);
        }
        return Identifier.tryParse(asked);
    }

    // what a body without a worn head or a place's own png is cut from: the player's skin, then
    // the game's default
    private static Identifier bodySheet(Appearance look, @Nullable AbstractClientPlayer wearer) {
        if (!look.skin.isEmpty()) {
            Identifier asked = Identifier.tryParse(look.skin);
            if (asked != null) return asked;
        }
        if (wearer != null) return wearer.getSkin().body().texturePath();
        return DefaultPlayerSkin.getDefaultSkin().body().texturePath();
    }

    // the second layer is the player's to switch off, one part at a time, in skin customisation.
    // drawing it anyway puts a hat back on someone who took it off, and a jacket over a skin drawn
    // to be seen without one
    private static boolean shows(@Nullable AbstractClientPlayer wearer, Limb limb) {
        if (wearer == null) return true;
        if (limb instanceof Cape) return wearer.isModelPartShown(PlayerModelPart.CAPE);
        if (!Rig.OVERLAY.equals(limb.name())) return true;
        Instance covers = limb.parent();
        PlayerModelPart part = covers == null ? null : switch (covers.name()) {
            case "head" -> PlayerModelPart.HAT;
            case "torso" -> PlayerModelPart.JACKET;
            case "rightArm" -> PlayerModelPart.RIGHT_SLEEVE;
            case "leftArm" -> PlayerModelPart.LEFT_SLEEVE;
            case "rightLeg" -> PlayerModelPart.RIGHT_PANTS_LEG;
            case "leftLeg" -> PlayerModelPart.LEFT_PANTS_LEG;
            default -> null;
        };
        return part == null || wearer.isModelPartShown(part);
    }

    // whether the camera is in your own head this frame
    //
    // the place's camera decides, and the game's own view decides when the place is not driving it:
    // a scriptable camera has been taken somewhere, and wherever that is, it is not inside you
    static boolean inside() {
        Camera camera = ClientPlace.camera();
        if (camera != null && camera.mode != CameraMode.FIRST_PERSON) return false;
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static void emit(Character character, Limb limb, float solid, List<Worn> into,
                             float partialTick) {
        // sampled at the frame's own fraction of the tick, the way every other part is. reading the
        // live property drew the body at twenty a second while the world around it was smooth,
        // which reads as the body lagging behind the camera rather than as a missing sample
        CFrame frame = ClientScene.motion().sample(limb, partialTick);
        Vec3 at = frame.position();
        Quat r = frame.rotation();
        Vec3 size = limb.size;
        Matrix4f transform = new Matrix4f()
                .translationRotateScale(
                        (float) at.x(), (float) at.y(), (float) at.z(),
                        (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w(),
                        (float) size.x(), (float) size.y(), (float) size.z());
        Color tint = limb.color;
        into.add(new Worn(transform,
                new Vector4f((float) tint.r(), (float) tint.g(), (float) tint.b(),
                        (float) (1.0 - limb.transparency) * solid),
                // the body's light, not the limb's: one answer for all of it
                PartLight.of(character, at),
                new Vector4f((float) limb.u, (float) limb.v,
                        (float) limb.texels.x(), (float) limb.texels.y()),
                new Vector2f((float) limb.texels.z(), limb.cutout ? 1f : 0f),
                new Vector2f(OVERLAY),
                new Vector4f((float) limb.sheetWidth, (float) limb.sheetHeight,
                        limb.mirrored ? 1f : 0f, 0f)));
        drawn++;
    }

    private static void register(Identifier texture) {
        Identifier id = Identifier.fromNamespaceAndPath("moud",
                "skin_" + texture.getNamespace() + "_" + texture.getPath().replace('/', '_'));
        BATCHES.put(texture, id);
        InstancedMesh.Builder<Worn> mesh = InstancedMesh.<Worn>builder(LAYOUT,
                        (inst, p) -> p.putMat4(inst.transform()).putVec4(inst.color())
                                .putVec2(inst.light().x, inst.light().y)
                                .putVec4(inst.uv())
                                .putVec2(inst.box().x, inst.box().y)
                                .putVec2(inst.overlay().x, inst.overlay().y)
                                .putVec4(inst.sheet()))
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/skin"))
                .texture(texture)
                .extraSampler("LightMap", Parts::lightMap, 1)
                .geometry(MeshData.unitCube())
                // alpha blended, but still writing depth: a solid body is unaffected by the blend
                // and a ghost of one needs it. dropping the depth write for the ghost's sake would
                // cost every solid body the ordering it depends on
                .renderState(RenderState.builder()
                        .blend(RenderState.BlendMode.ALPHA)
                        .build())
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .worldSpace()
                .onRender((ctx, batch) -> draw(texture, ctx, batch));
        // one or the other, never both: a body that casts its own shadow and also drops the
        // game's flat circle under itself is drawn twice on the ground
        //
        // read once, when the batch is made, because a batch cannot change its mind about
        // shadows later. a place states this at startup like any other switch
        if (!MoudMod.features().isOn(Feature.BLOB_SHADOWS)) mesh.castsShadow();
        mesh.register(id);
    }

    private static void draw(Identifier texture, InstanceRenderContext ctx,
                             InstanceBatch<Worn> batch) {
        List<Worn> worn = PACKED.get(texture);
        if (worn == null) return;
        for (Worn one : worn) batch.add(one);
    }

    private static Character characterOf(Part part) {
        return part.parent() instanceof Character character ? character
                : part.parent() instanceof Part parent && parent.parent() instanceof Character owner
                        ? owner : null;
    }

    // what this body is drawn in, in the order a body gets one: what the place asked for, then
    // the skin of the player who drives it, then the game's own default
    //
    // there is no fourth case and no flat fallback. a body is a body: it is always drawn through
    // the unwrap, and a place that wants a grey statue hands it a grey png or tints the limbs
    private static Identifier textureOf(Appearance look, @Nullable AbstractClientPlayer wearer) {
        if (!look.skin.isEmpty()) {
            Identifier asked = Identifier.tryParse(look.skin);
            // unreachable: the write checked it, so text that got this far names a file. it throws
            // rather than quietly falling back to the wearer's skin, because a body drawn in
            // somebody else's skin is how a typo in a place gets shipped -- it looks like it worked
            if (asked == null) {
                throw new IllegalStateException("skin \"" + look.skin + "\" got past the write check");
            }
            return asked;
        }
        if (wearer != null) return wearer.getSkin().body().texturePath();
        return DefaultPlayerSkin.getDefaultSkin().body().texturePath();
    }

    public static @Nullable AbstractClientPlayer wearerOf(Character character) {
        if (character == null || character.owner.isEmpty()) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        try {
            if (client.level.getPlayerByUUID(UUID.fromString(character.owner))
                    instanceof AbstractClientPlayer player) {
                return player;
            }
        } catch (IllegalArgumentException ignored) {
            // an owner that is not a uuid is a place's own character, and it wears no skin
        }
        return null;
    }
}
