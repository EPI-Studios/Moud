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

public final class Skins {

    private static final InstanceLayout LAYOUT =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).vec4(7).vec2(8).vec2(9).vec4(10).build();

    private record Worn(Matrix4f transform, Vector4f color, Vector2f light,
                        Vector4f uv, Vector2f box, Vector2f overlay, Vector4f sheet) {}

    private static final Map<Identifier, Identifier> BATCHES = new HashMap<>();
    private static final Map<Identifier, List<Worn>> PACKED = new HashMap<>();

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
            if (character == mine && inside() && look.firstPerson != FirstPerson.BODY) continue;

            AbstractClientPlayer wearer = wearerOf(character);
            Identifier body = bodySheet(look, wearer);

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
            if (child instanceof Spatial) walk(character, child, wearer, body, solid, partialTick);
        }
    }

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

    private static Identifier bodySheet(Appearance look, @Nullable AbstractClientPlayer wearer) {
        if (!look.skin.isEmpty()) {
            Identifier asked = Identifier.tryParse(look.skin);
            if (asked != null) return asked;
        }
        if (wearer != null) return wearer.getSkin().body().texturePath();
        return DefaultPlayerSkin.getDefaultSkin().body().texturePath();
    }

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

    static boolean inside() {
        Camera camera = ClientPlace.camera();
        if (camera != null && camera.mode != CameraMode.FIRST_PERSON) return false;
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static void emit(Character character, Limb limb, float solid, List<Worn> into,
                             float partialTick) {
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
                .renderState(RenderState.builder()
                        .blend(RenderState.BlendMode.ALPHA)
                        .build())
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .worldSpace()
                .onRender((ctx, batch) -> draw(texture, ctx, batch));
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

    private static Identifier textureOf(Appearance look, @Nullable AbstractClientPlayer wearer) {
        if (!look.skin.isEmpty()) {
            Identifier asked = Identifier.tryParse(look.skin);
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
        }
        return null;
    }
}
