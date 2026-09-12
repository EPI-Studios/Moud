package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.CharacterDisplay;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.MoudMod;
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
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).vec4(7).vec2(8).vec2(9).build();

    private record Worn(Matrix4f transform, Vector4f color, Vector2f light,
                        Vector4f uv, Vector2f box, Vector2f overlay) {}

    private static final Map<Identifier, Identifier> BATCHES = new HashMap<>();
    private static final Map<Identifier, List<Worn>> PACKED = new HashMap<>();

    private static final double TEXEL = 1.0 / 16.0;

    // one body's wash, set once per character and read by each of its twelve parts
    private static final Vector2f OVERLAY = new Vector2f();

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();

    private static int drawn;

    private Skins() {}

    public static int count() {
        return drawn;
    }

    // a rig part is drawn here and must not also be drawn flat by the ordinary batches, or the
    // body is painted twice and the skin loses to whichever went second
    public static boolean wearsSkin(Part part) {
        // only the body itself. a sword in a hand hangs off a limb and is still an ordinary part,
        // and swallowing it here would make it invisible rather than skinned
        if (SkinLayout.of(bodyName(part), false, false) == null) return false;
        return characterOf(part) != null;
    }

    // the overlay shell is named for what it covers, so it answers as its parent does
    private static String bodyName(Part part) {
        return Rig.OVERLAY.equals(part.name()) && part.parent() != null
                ? part.parent().name() : part.name();
    }

    // gathered once per frame, then handed to whichever batch wears that skin
    public static void gather(float partialTick) {
        PACKED.values().forEach(List::clear);
        drawn = 0;

        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;

        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            if (character.display != CharacterDisplay.MODEL) continue;
            AbstractClientPlayer wearer = wearerOf(character);
            Identifier texture = textureOf(character, wearer);
            boolean slim = wearer != null
                    ? wearer.getSkin().model() == PlayerModelType.SLIM
                    : character.slim;

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

            List<Worn> into = PACKED.computeIfAbsent(texture, id -> {
                register(id);
                return new ArrayList<>();
            });
            for (Instance child : character.children()) {
                if (child instanceof Part part) {
                    pack(part, slim, character.scale, solid, wearer, into, partialTick);
                }
            }
        }
    }

    private static void pack(Part part, boolean slim, double scale, float solid,
                             @Nullable AbstractClientPlayer wearer, List<Worn> into,
                             float partialTick) {
        SkinLayout.Box box = SkinLayout.of(part.name(), false, slim);
        if (box == null) return;
        double narrow = slim ? narrowing(part.name()) * scale : 0;
        emit(part, box, false, narrow, solid, into, partialTick);

        // the shell is the place's to switch off and the player's to switch off, and it never
        // outlives the limb it covers
        if (!part.visible || !shows(wearer, part.name())) return;
        if (part.child(Rig.OVERLAY) instanceof Part shell) {
            SkinLayout.Box over = SkinLayout.of(part.name(), true, slim);
            if (over != null) emit(shell, over, true, narrow, solid, into, partialTick);
        }
    }

    // the second layer is the player's to switch off, one part at a time, in skin customisation.
    // drawing it anyway puts a hat back on someone who took it off, and a jacket over a skin
    // drawn to be seen without one
    private static boolean shows(@Nullable AbstractClientPlayer wearer, String name) {
        if (wearer == null) return true;
        PlayerModelPart part = switch (name) {
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

    // a slim skin narrows an arm to three texels, and the texel it loses is the one away from the
    // torso, so the box also slides half a texel inward to stay flush against it
    //
    // the tree keeps the wide box on purpose: which skin someone wears is a thing this client can
    // see and the server cannot, and a body must not collide differently for it. so the narrowing
    // lives here, on the way to the batch, and never in the rig
    private static double narrowing(String name) {
        if ("rightArm".equals(name)) return -TEXEL;
        return "leftArm".equals(name) ? TEXEL : 0;
    }

    private static void emit(Part part, SkinLayout.Box box, boolean shell, double narrow,
                             float solid, List<Worn> into, float partialTick) {
        if (!part.visible) return;
        // sampled at the frame's own fraction of the tick, the way every other part is. reading
        // the live property drew the body at twenty a second while the world around it was smooth,
        // which reads as the body lagging behind the camera rather than as a missing sample
        CFrame frame = ClientScene.motion().sample(part, partialTick);
        // the slide is along the arm's own x, so it follows the arm through the swing rather than
        // drifting sideways in the world when the body turns
        if (narrow != 0) frame = frame.mul(CFrame.at(narrow * 0.5, 0, 0));
        Vec3 at = frame.position();
        Quat r = frame.rotation();
        Vec3 size = narrow == 0 ? part.size
                : new Vec3(part.size.x() - Math.abs(narrow), part.size.y(), part.size.z());
        Matrix4f transform = new Matrix4f()
                .translationRotateScale(
                        (float) at.x(), (float) at.y(), (float) at.z(),
                        (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w(),
                        (float) size.x(), (float) size.y(), (float) size.z());
        Color tint = part.color;
        into.add(new Worn(transform,
                new Vector4f((float) tint.r(), (float) tint.g(), (float) tint.b(),
                        (float) (1.0 - part.transparency) * solid),
                PartLight.of(part, at),
                new Vector4f(box.u(), box.v(), box.w(), box.h()),
                new Vector2f(box.d(), shell ? 1f : 0f),
                new Vector2f(OVERLAY)));
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
                                .putVec2(inst.overlay().x, inst.overlay().y))
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
    private static Identifier textureOf(Character character, @Nullable AbstractClientPlayer wearer) {
        if (!character.skin.isEmpty()) {
            Identifier asked = Identifier.tryParse(character.skin);
            if (asked != null) return asked;
        }
        if (wearer != null) return wearer.getSkin().body().texturePath();
        return DefaultPlayerSkin.getDefaultSkin().body().texturePath();
    }

    private static @Nullable AbstractClientPlayer wearerOf(Character character) {
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
