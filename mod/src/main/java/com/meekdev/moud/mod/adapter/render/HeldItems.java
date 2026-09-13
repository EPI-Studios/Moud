package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.CharacterDisplay;
import com.meekdev.moud.core.character.FirstPerson;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.client.ClientScene;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;

public final class HeldItems {

    private record Hand(int id, boolean left) {}

    private static final Map<Hand, ItemStackRenderState> STATES = new HashMap<>();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private HeldItems() {}

    public static void submit(PoseStack poses, SubmitNodeCollector out, Vec3 camera) {
        InstanceTree tree = ClientScene.tree();
        Minecraft client = Minecraft.getInstance();
        if (tree == null || client.level == null) {
            STATES.clear();
            return;
        }
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Character mine = ClientScene.own();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            Appearance look = Rig.appearance(character);
            if (look == null || look.display != CharacterDisplay.MODEL) continue;
            if (character == mine && Skins.inside() && look.firstPerson != FirstPerson.BODY) continue;
            AbstractClientPlayer wearer = Skins.wearerOf(character);
            hand(character, wearer, false, poses, out, camera, partialTick);
            hand(character, wearer, true, poses, out, camera, partialTick);
        }
        STATES.keySet().removeIf(hand -> tree.byId(hand.id()) == null);
    }

    private static void hand(Character character, AbstractClientPlayer wearer, boolean left,
            PoseStack poses, SubmitNodeCollector out, Vec3 camera, float partialTick) {
        String forced = left ? character.leftItemOverride : character.rightItemOverride;
        String id = !forced.isEmpty() ? forced : left ? character.leftItem : character.rightItem;
        if (id.isEmpty()) return;
        if (!(character.child(left ? "leftArm" : "rightArm") instanceof Part arm)) return;
        if (!arm.visible || arm.transparency >= 1) return;
        if (!(arm.child(Rig.GRIP) instanceof Instance grip)) return;

        ItemStack stack = stack(wearer, left, id);
        if (stack.isEmpty()) return;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        ItemStackRenderState state = STATES.computeIfAbsent(new Hand(character.id(), left),
                hand -> new ItemStackRenderState());
        Minecraft client = Minecraft.getInstance();
        if (wearer != null) {
            client.getItemModelResolver().updateForLiving(state, stack, context, wearer);
        } else {
            client.getItemModelResolver().updateForTopItem(state, stack, context, client.level, null,
                    character.id());
        }
        if (state.isEmpty()) return;

        CFrame world = ClientScene.motion().sample(grip, partialTick);
        Vector3 at = world.position();
        Quat turn = world.rotation();
        poses.pushPose();
        poses.translate(at.x() - camera.x, at.y() - camera.y, at.z() - camera.z);
        poses.mulPose(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()));
        poses.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        float scale = (float) character.scale;
        poses.scale(scale, scale, scale);
        int light = LevelRenderer.getLightCoords(client.level, BlockPos.containing(at.x(), at.y(), at.z()));
        state.submit(poses, out, light, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
    }

    private static ItemStack stack(AbstractClientPlayer wearer, boolean left, String id) {
        if (wearer != null) {
            ItemStack held = Characters.handOf(wearer, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            if (!held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(id)) return held;
        }
        return STACKS.computeIfAbsent(id, name -> {
            Identifier key = Identifier.tryParse(name);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
        });
    }
}
