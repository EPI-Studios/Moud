package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import java.util.UUID;

public final class Character extends Spatial {

    @Prop(min = 0.05) public double radius = 0.3;
    @Prop(min = 0.1) public double height = 1.8;

    @Prop(min = 0.05) public double scale = 1.0;

    public String collisionGroup = "default";

    public boolean animate = true;

    @Prop(driven = true) public double lookPitch;
    @Prop(driven = true) public double lookYaw;

    @Prop(driven = true) public double moveDistance;
    @Prop(driven = true, min = 0, max = 1) public double moveSpeed;

    @Prop(min = 0.01) public double speedValue = 1.0;

    @Prop(driven = true) public boolean crouching;

    @Prop(driven = true) public Vector3 velocity = Vector3.ZERO;

    public Instance team;

    @Prop(driven = true, min = 0, max = 1) public double attackTime;

    @Prop(driven = true) public boolean attackLeft;

    @Prop(driven = true) public boolean mainLeft;

    @Prop(driven = true) public ArmPose rightArmPose = ArmPose.EMPTY;
    @Prop(driven = true) public ArmPose leftArmPose = ArmPose.EMPTY;

    @Prop(driven = true, asset = true) public String rightItem = "";
    @Prop(driven = true, asset = true) public String leftItem = "";

    @Prop(asset = true) public String rightItemOverride = "";
    @Prop(asset = true) public String leftItemOverride = "";

    @Prop(driven = true) public boolean usingItem;
    @Prop(driven = true) public boolean useLeftHand;

    @Prop(driven = true, min = 0, max = 1) public double chargeProgress;

    @Prop(driven = true, min = 0, max = 1) public double swimAmount;

    @Prop(driven = true) public boolean riding;

    @Prop(driven = true) public boolean flying;

    @Prop(driven = true) public boolean inWater;

    @Prop(driven = true, min = 0) public double flyingTime;

    @Prop(driven = true) public double flyingYaw;

    @Prop(driven = true, min = 0) public double deathTime;

    @Prop(driven = true) public boolean sleeping;

    @Prop(driven = true) public double bedYaw;

    @Prop(driven = true) public boolean crawling;

    @Prop(driven = true) public boolean spinning;

    @Prop(driven = true) public boolean frozen;

    public boolean upsideDown;

    @Prop(driven = true) public boolean hurt;

    @Prop(min = 0, max = 1) public double whiteFlash;

    public final boolean hasPlayer() {
        if (owner.isEmpty()) return false;
        try {
            UUID.fromString(owner);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    protected long externalPropertyMask() {
        if (!hasPlayer()) return 0;
        return def().driven() | (1L << Classes.SPATIAL.property("cframe").index());
    }

    @Override
    protected void build() {
        Rig.build(this);
    }
}
