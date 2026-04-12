package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.player.MoudPalAnimLayer;
import com.moud.client.fabric.util.ClientDebugLog;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

public final class AnimApi {

    private static final String TAG = "AnimApi";
    private static final float DEFAULT_STOP_FADE = 0.2f;

    private final AbstractClientPlayerEntity player;

    public AnimApi(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    public void play(String layer, String animName) {
        playWithFade(layer, animName, 0.0f);
    }

    public void playWithFade(String layer, String animName, float fadeSecs) {
        PlayerAnimationController ctrl = playerController();
        Identifier animId = animationId(animName);
        if (ctrl == null || animId == null) {
            return;
        }
        try {
            if (!PlayerAnimResources.hasAnimation(animId)) {
                return;
            }
            ctrl.triggerAnimation(animId, Math.max(0.0f, fadeSecs));
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to play animation '" + animName + "': " + e.getMessage());
        }
    }

    public void stop(String layer) {
        stopWithFade(layer, DEFAULT_STOP_FADE);
    }

    public void stopWithFade(String layer, float fadeSecs) {
        AnimationController ctrl = controller();
        if (ctrl == null) {
            return;
        }
        try {
            ctrl.stop();
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to stop animation '" + layer + "': " + e.getMessage());
        }
    }

    public boolean isPlaying(String layer) {
        AnimationController ctrl = controller();
        Identifier animId = animationId(layer);
        if (ctrl == null || animId == null) {
            return false;
        }
        try {
            var target = PlayerAnimResources.getAnimationOptional(animId).orElse(null);
            if (target == null || !ctrl.isActive()) {
                return false;
            }
            if (target.equals(ctrl.getCurrentAnimationInstance())) {
                return true;
            }
            RawAnimation raw = ctrl.getCurrentRawAnimation();
            if (raw == null) {
                return false;
            }
            for (RawAnimation.Stage stage : raw.getAnimationStages()) {
                if (stage != null && target.equals(stage.animation())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to query animation '" + layer + "': " + e.getMessage());
            return false;
        }
    }

    public float[] getBonePos(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return new float[]{0f, 0f, 0f};
        }
        try {
            return new float[]{bone.getPosX(), bone.getPosY(), bone.getPosZ()};
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to read bone position '" + name + "': " + e.getMessage());
            return new float[]{0f, 0f, 0f};
        }
    }

    public float[] getBoneRot(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return new float[]{0f, 0f, 0f};
        }
        try {
            return new float[]{bone.getRotX(), bone.getRotY(), bone.getRotZ()};
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to read bone rotation '" + name + "': " + e.getMessage());
            return new float[]{0f, 0f, 0f};
        }
    }

    public void setBonePos(String name, float x, float y, float z) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return;
        }
        try {
            bone.setPosX(x);
            bone.setPosY(y);
            bone.setPosZ(z);
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to set bone position '" + name + "': " + e.getMessage());
        }
    }

    public void setBoneRot(String name, float x, float y, float z) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return;
        }
        try {
            bone.setRotX(x);
            bone.setRotY(y);
            bone.setRotZ(z);
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to set bone rotation '" + name + "': " + e.getMessage());
        }
    }

    public void resetBone(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return;
        }
        try {
            bone.setPosX(0.0f);
            bone.setPosY(0.0f);
            bone.setPosZ(0.0f);
            bone.setRotX(0.0f);
            bone.setRotY(0.0f);
            bone.setRotZ(0.0f);
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to reset bone '" + name + "': " + e.getMessage());
        }
    }

    private AnimationController controller() {
        if (player == null) {
            return null;
        }
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoudPalAnimLayer.LAYER_ID);
            if (layer instanceof AnimationController ctrl) {
                return ctrl;
            }
            return null;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access PAL animation controller: " + e.getMessage());
            return null;
        }
    }

    private PlayerAnimationController playerController() {
        if (player == null) {
            return null;
        }
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoudPalAnimLayer.LAYER_ID);
            if (layer instanceof PlayerAnimationController ctrl) {
                return ctrl;
            }
            return null;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access PAL player animation controller: " + e.getMessage());
            return null;
        }
    }

    private PlayerAnimBone bone(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        AnimationController ctrl = controller();
        if (ctrl == null) {
            return null;
        }
        try {
            return ctrl.getBone(name);
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access bone '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private static Identifier animationId(String animName) {
        if (animName == null || animName.isBlank()) {
            return null;
        }
        try {
            return Identifier.of("moud", animName.trim());
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Invalid animation id '" + animName + "': " + e.getMessage());
            return null;
        }
    }
}
