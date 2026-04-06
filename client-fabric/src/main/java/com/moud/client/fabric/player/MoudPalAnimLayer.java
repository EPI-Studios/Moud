package com.moud.client.fabric.player;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

public final class MoudPalAnimLayer {

    public static final Identifier LAYER_ID = Identifier.of("moud", "attachment_observer");

    private MoudPalAnimLayer() {
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                0,
                player -> new PlayerAnimationController(player, (controller, state, setter) -> {
                    return PlayState.STOP;
                })
        );
    }

    static void applyBoneOffsets(AbstractClientPlayerEntity player, String uuid,
                                 float baseX, float baseY, float baseZ,
                                 float sinYaw, float cosYaw) {
        if (player == null) {
            return;
        }

        AnimationController ctrl;
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID);
            if (!(layer instanceof AnimationController ac)) {
                return;
            }
            ctrl = ac;
        } catch (Exception ignored) {
            return;
        }

        applyBone(ctrl, uuid, "head",       baseX, baseY + 1.45f, baseZ,  0f,     0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "right_arm", baseX, baseY + 0.95f, baseZ,  0.35f,  0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "left_arm",  baseX, baseY + 0.95f, baseZ, -0.35f,  0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "right_item",baseX, baseY + 0.9f,  baseZ,  0.65f,  0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "left_item", baseX, baseY + 0.9f,  baseZ, -0.65f,  0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "right_leg", baseX, baseY + 0.25f, baseZ,  0.15f,  0f, sinYaw, cosYaw);
        applyBone(ctrl, uuid, "left_leg",  baseX, baseY + 0.25f, baseZ, -0.15f,  0f, sinYaw, cosYaw);
    }

    private static void applyBone(AnimationController ctrl, String uuid,
                                  String boneName,
                                  float restX, float restY, float restZ,
                                  float restLocalX, float restLocalZ,
                                  float sinYaw, float cosYaw) {
        try {
            PlayerAnimBone bone = ctrl.getBone(boneName);
            if (bone == null) {
                return;
            }
            float bx = bone.getPosX() / 16f;
            float by = bone.getPosY() / 16f;
            float bz = bone.getPosZ() / 16f;

            float localX = restLocalX + bx;
            float localZ = restLocalZ + bz;
            float worldX = restX + localX * (-cosYaw) + localZ * sinYaw;
            float worldY = restY + by;
            float worldZ = restZ + localX * (-sinYaw) + localZ * (-cosYaw);

            String attachPoint = switch (boneName) {
                case "head"       -> "head";
                case "right_arm"  -> "right_hand";
                case "left_arm"   -> "left_hand";
                case "right_item" -> "right_item";
                case "left_item"  -> "left_item";
                case "right_leg"  -> "right_foot";
                case "left_leg"   -> "left_foot";
                default           -> null;
            };
            if (attachPoint != null) {
                PlayerBodyAttachmentCache.storePalBonePoint(uuid, attachPoint, worldX, worldY, worldZ);
                float rxDeg = (float) Math.toDegrees(bone.getRotX());
                float ryDeg = (float) Math.toDegrees(bone.getRotY());
                float rzDeg = (float) Math.toDegrees(bone.getRotZ());
                PlayerBodyAttachmentCache.storePalBoneRot(uuid, attachPoint, rxDeg, ryDeg, rzDeg);
            }
        } catch (Exception ignored) {
        }
    }
}