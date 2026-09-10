package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.CharacterDisplay;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
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
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).vec4(7).vec2(8).build();

    private record Worn(Matrix4f transform, Vector4f color, Vector2f light,
                        Vector4f uv, Vector2f box) {}

    private static final Map<Identifier, Identifier> BATCHES = new HashMap<>();
    private static final Map<Identifier, List<Worn>> PACKED = new HashMap<>();

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
        Character character = characterOf(part);
        return character != null && skinOf(character) != null;
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
            PlayerSkin skin = skinOf(character);
            if (skin == null) continue;

            Identifier texture = skin.body().texturePath();
            boolean slim = skin.model() == PlayerModelType.SLIM;
            List<Worn> into = PACKED.computeIfAbsent(texture, id -> {
                register(id);
                return new ArrayList<>();
            });
            for (Instance child : character.children()) {
                if (child instanceof Part part) pack(part, slim, into, partialTick);
            }
        }
    }

    private static void pack(Part part, boolean slim, List<Worn> into, float partialTick) {
        SkinLayout.Box box = SkinLayout.of(part.name(), false, slim);
        if (box == null) return;
        emit(part, box, false, into, partialTick);

        if (part.child(Rig.OVERLAY) instanceof Part shell) {
            SkinLayout.Box over = SkinLayout.of(part.name(), true, slim);
            if (over != null) emit(shell, over, true, into, partialTick);
        }
    }

    private static void emit(Part part, SkinLayout.Box box, boolean shell, List<Worn> into,
                             float partialTick) {
        if (!part.visible) return;
        CFrame frame = Transforms.world(part);
        Vec3 at = frame.position();
        Quat r = frame.rotation();
        Matrix4f transform = new Matrix4f()
                .translationRotateScale(
                        (float) at.x(), (float) at.y(), (float) at.z(),
                        (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w(),
                        (float) part.size.x(), (float) part.size.y(), (float) part.size.z());
        Color tint = part.color;
        into.add(new Worn(transform,
                new Vector4f((float) tint.r(), (float) tint.g(), (float) tint.b(),
                        (float) (1.0 - part.transparency)),
                PartLight.of(part, at),
                new Vector4f(box.u(), box.v(), box.w(), box.h()),
                new Vector2f(box.d(), shell ? 1f : 0f)));
        drawn++;
    }

    private static void register(Identifier texture) {
        Identifier id = Identifier.fromNamespaceAndPath("moud",
                "skin_" + texture.getNamespace() + "_" + texture.getPath().replace('/', '_'));
        BATCHES.put(texture, id);
        InstancedMesh.<Worn>builder(LAYOUT,
                        (inst, p) -> p.putMat4(inst.transform()).putVec4(inst.color())
                                .putVec2(inst.light().x, inst.light().y)
                                .putVec4(inst.uv())
                                .putVec2(inst.box().x, inst.box().y))
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/skin"))
                .texture(texture)
                .extraSampler("LightMap", Parts::lightMap, 1)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .worldSpace()
                .castsShadow()
                .onRender((ctx, batch) -> draw(texture, ctx, batch))
                .register(id);
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

    private static PlayerSkin skinOf(Character character) {
        if (character == null || character.owner.isEmpty()) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        try {
            if (client.level.getPlayerByUUID(UUID.fromString(character.owner))
                    instanceof AbstractClientPlayer player) {
                return player.getSkin();
            }
        } catch (IllegalArgumentException ignored) {
            // an owner that is not a uuid is a place's own character, and it wears no skin
        }
        return null;
    }
}
