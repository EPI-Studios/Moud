package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Prop;
import java.util.UUID;

// what a player controls. these are the movement profile, in metres and seconds like everything
// else, and the adapter is the only thing that knows bkun states the same numbers per tick
//
// the shape is ours and is not vanilla's 0.6 box. the mover is a capsule, so a box character
// leaves its corners outside the collision and clips walls on a diagonal -- we declare the capsule
// and draw a body that fits it instead
public final class Character extends Spatial {

    @Prop(min = 0.05) public double radius = 0.3;
    @Prop(min = 0.1) public double height = 1.8;

    // the rig, over the proportions the engine built it at. the capsule is radius and height and
    // does not follow this: a place that wants a bigger character says so on both, deliberately
    @Prop(min = 0.05) public double scale = 1.0;

    // the CollisionGroup this body moves in, by name
    public String collisionGroup = "default";

    // the engine poses the body from the state below. a place that wants the limbs to itself
    // turns this off and writes them, rather than fighting a pose that is rewritten every tick
    public boolean animate = true;

    // where the head looks, relative to the body
    @Prop(driven = true) public double lookPitch;
    @Prop(driven = true) public double lookYaw;

    // how far this body has walked and how fast, which is what a limb swings on. distance rather
    // than time, so a body animates the same however long the frame took
    @Prop(driven = true) public double moveDistance;
    @Prop(driven = true, min = 0, max = 1) public double moveSpeed;

    // what the limb swing is divided by, which is how a body that covers more ground per step
    // swings its legs less rather than faster. one unless something scales the body's stride
    @Prop(min = 0.01) public double speedValue = 1.0;

    @Prop(driven = true) public boolean crouching;

    // the rest of what the body is doing, which is all the model animates from
    //
    // every one of these is a plain property, so a place writes them exactly as readily as the
    // engine does: a cutscene can put a body mid swing, a chair can sit it down, a script can
    // drown it. that is what customising a pose is here -- no limb is taken from anyone
    @Prop(driven = true, min = 0, max = 1) public double attackTime;

    // which arm the swing belongs to, which is not always the main one: swinging the off hand
    // puts the blow on the other side
    @Prop(driven = true) public boolean attackLeft;

    // which arm is the main one, which decides the order the two are posed in
    @Prop(driven = true) public boolean mainLeft;

    // what each arm is doing with what it holds
    @Prop(driven = true) public ArmPose rightArmPose = ArmPose.EMPTY;
    @Prop(driven = true) public ArmPose leftArmPose = ArmPose.EMPTY;

    // holding a use down, and in which hand. the model poses the used hand first and only lets
    // the other one have its own pose if the first did not already write it
    @Prop(driven = true) public boolean usingItem;
    @Prop(driven = true) public boolean useLeftHand;

    // how far through winding a crossbow, which is the only thing the charge pose reads. the
    // model keeps ticks and a maximum and uses nothing but their ratio
    @Prop(driven = true, min = 0, max = 1) public double chargeProgress;

    @Prop(driven = true, min = 0, max = 1) public double swimAmount;

    // sat on something, which folds the legs rather than swinging them
    @Prop(driven = true) public boolean riding;

    // under an elytra, which pitches the head down and the body flat
    @Prop(driven = true) public boolean flying;

    // a body swimming in water pitches to its own look, one in air to straight down
    @Prop(driven = true) public boolean inWater;

    // how long the body has been under an elytra, in ticks. the tilt comes on over the first
    // ten of them rather than at once
    @Prop(driven = true, min = 0) public double flyingTime;

    // the angle between where the body is going and where it is looking, which is what banks it
    @Prop(driven = true) public double flyingYaw;

    // how long it has been dead, in ticks. the fall takes twenty
    @Prop(driven = true, min = 0) public double deathTime;

    @Prop(driven = true) public boolean sleeping;

    // which way the bed points, in radians. a sleeping body does not lie at its own yaw turned a
    // bit: the model throws the body's yaw away and uses the bed's, which is why this is a
    // heading of its own and not an offset on one
    @Prop(driven = true) public double bedYaw;

    // flat and low in the swimming pose, which decides where the body is drawn rather than how
    // its limbs are animated
    @Prop(driven = true) public boolean crawling;

    // spinning on a riptide trident
    @Prop(driven = true) public boolean spinning;

    // frozen solid, which shakes the body about its own up
    @Prop(driven = true) public boolean frozen;

    // the way a name nobody says out loud hangs a body
    public boolean upsideDown;

    // just hit, which washes the body red. the model carries this and the one below as a pair of
    // coordinates into a sixteen by sixteen table; they are the table's two axes
    @Prop(driven = true) public boolean hurt;

    // how far the body is washed white, which is what a mob about to go off does. zero for a
    // player unless a place says otherwise
    @Prop(min = 0, max = 1) public double whiteFlash;

    // whether a player is wearing this body
    //
    // the owner is that player's id, and a place's own character carries a label instead -- which
    // is what tells the two apart here, on either side, without asking the game anything
    public final boolean worn() {
        if (owner.isEmpty()) return false;
        try {
            UUID.fromString(owner);
            return true;
        } catch (IllegalArgumentException notAPlayer) {
            return false;
        }
    }

    // everything a player wearing this body already tells every client
    //
    // §4.3: the character *is* the player entity, with its movement replaced and its rendering
    // replaced. so the game is already sending that entity's position, rotation, crouch, swing, held
    // item and walk to everybody who can see it, and every property marked driven above is worked
    // out from exactly those. ours would be the same facts a second time, down a channel whose
    // ordering is the wrong promise for a stream of samples: one lost packet holds up every later
    // one, and the newest sample arriving last is the only thing a stream of samples cannot use
    //
    // cframe is in it and is not marked on the field, because the field belongs to Spatial: where a
    // *part* is is nobody else's business. only a worn body's frame is somebody else's business
    @Override
    protected long propertiesFromElsewhere() {
        // a body nobody is wearing has no entity behind it, so nothing else is carrying any of
        // this: a place's own character replicates like everything else in the tree
        if (!worn()) return 0;
        return def().driven() | (1L << Classes.SPATIAL.property("cframe").index());
    }

    @Override
    protected void build() {
        Rig.build(this);
    }
}
