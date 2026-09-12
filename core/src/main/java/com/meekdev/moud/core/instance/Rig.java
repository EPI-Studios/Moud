package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

// the body, ported rather than invented
//
// every number here is the one the game itself builds a player out of: the pivot each limb turns
// at, the box hung off that pivot, and how far the shell over it is pushed out. they are stated in
// the space that model is authored in -- sixteen units to the metre, y downward, x mirrored, the
// shoulder line at zero and the feet twenty four below -- and converted once, here, so the rest of
// the engine only ever sees metres with y up
//
// getting these approximately right is what makes a body look like a bad copy. they are exact
public final class Rig {

    public static final String HITBOX = "hitbox";

    // where the joints live, so a place reaches one by name the way it reaches a limb
    public static final String JOINTS = "joints";

    // the joint the whole body hangs from, which is what carries a tilt none of the limbs own
    public static final String ROOT = "root";

    // the second shell every part wears: hat, jacket, sleeves and trousers
    public static final String OVERLAY = "overlay";

    // where a hand holds something. an empty frame under each arm, and anything parented to one
    // is held: the hierarchy already carries it through the swing, the crouch and the scale
    //
    // it sits exactly where the game puts an item in that hand, so a thing built out of parts
    // lands where a real one would be drawn -- the look is the game's, the mechanism is ours
    public static final String GRIP = "grip";

    // a pair of wings on the back. they are not limbs: the model states them from the body's own
    // root and steps them two texels back, and they are drawn off a sheet of their own
    public static final String[] WINGS = {"rightWing", "leftWing"};

    // hung off the back of the torso rather than off the body, so it leans with a crouch and
    // twists with a swing without being told to
    public static final String CAPE = "cape";

    // the pair's own state, beside the two boxes that draw it
    public static final String WING_SET = "wings";

    private static final double PX = 1.0 / 16.0;

    // the model is authored from the shoulder down; the feet are twenty four units below it
    private static final double STANDING = 24.0;

    private static final PropertyDef SIZE = Classes.PART.property("size");
    private static final PropertyDef CFRAME = Classes.PART.property("cframe");
    private static final PropertyDef PIVOT = Classes.PART.property("pivot");
    private static final PropertyDef VISIBLE = Classes.PART.property("visible");
    private static final PropertyDef C0 = Classes.JOINT.property("c0");
    private static final PropertyDef GRIP_FRAME = Classes.SPATIAL.property("cframe");

    // a limb: where it turns, the box hung off that, and how far its shell stands proud
    private record Limb(String name, Vec3 pivot, Vec3 box, Vec3 size, double shell) {}

    // the wing the model builds, in its own units. the box is ten by twenty by two grown by a
    // whole texel on every side, which is why the drawn size and the rect it is cut from are two
    // separate numbers and always will be
    private static final Limb[] WING = {
            limb("rightWing", -5, 0, -2, 0, 0, 0, 10, 20, 2, 1.0),
            limb("leftWing", 5, 0, -2, -10, 0, 0, 10, 20, 2, 1.0),
    };

    // ten by sixteen by one, hung two texels behind the torso's own joint. no growing: a cape is
    // the one thing the model does not inflate
    private static final Limb CAPE_BOX = limb("cape", 0, 0, -2, -5, 0, -1, 10, 16, 1, 0);

    private static final Limb[] BODY = {
            limb("head", 0, 0, 0, -4, -8, -4, 8, 8, 8, 0.5),
            limb("torso", 0, 0, 0, -4, 0, -2, 8, 12, 4, 0.25),
            limb("rightArm", -5, 2, 0, -3, -2, -2, 4, 12, 4, 0.25),
            limb("leftArm", 5, 2, 0, -1, -2, -2, 4, 12, 4, 0.25),
            limb("rightLeg", -1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25),
            limb("leftLeg", 1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25),
    };

    // the six, in one place. they were strings in three files, so renaming one stopped the pose
    // finding it and said nothing
    public static String[] limbs() {
        String[] names = new String[BODY.length];
        for (int n = 0; n < BODY.length; n++) names[n] = BODY[n].name();
        return names;
    }

    private Rig() {}

    // model space is mirrored on x and runs downward on y, which is why every conversion is here
    // and nowhere else
    private static Limb limb(String name, double px, double py, double pz,
                             double bx, double by, double bz, double w, double h, double d,
                             double shell) {
        Vec3 pivot = new Vec3(-px * PX, (STANDING - py) * PX, pz * PX);
        // the middle of the box, measured from the pivot it hangs on
        Vec3 centre = new Vec3(-(bx + w * 0.5) * PX, -(by + h * 0.5) * PX, (bz + d * 0.5) * PX);
        return new Limb(name, pivot, centre, new Vec3(w * PX, h * PX, d * PX), shell * PX);
    }

    // where a limb turns, at this scale
    //
    // a pose that moves a joint starts from here, never from where it left it last tick: the
    // model states a crouch as an offset applied to the standing pivot, and reading back the
    // offset one would compound it every tick until the body came apart
    // the pair this body wears, or nothing
    public static Wings wings(Character character) {
        return character.child(WING_SET) instanceof Wings pair ? pair : null;
    }

    private static boolean worn(Character character) {
        Wings pair = wings(character);
        return pair != null && pair.worn;
    }

    // the joint that drives a limb, or the body's own when asked for the root
    public static Instance joint(Character character, String name) {
        return character.child(JOINTS) instanceof Instance joints ? joints.child(name) : null;
    }

    public static Vec3 pivot(String name, double scale) {
        for (Limb limb : BODY) {
            if (limb.name().equals(name)) return limb.pivot().mul(scale);
        }
        return Vec3.ZERO;
    }

    // an offset stated the way the model states one, in its units and its directions, converted
    // here for the same reason every other number is
    public static Vec3 offset(double x, double y, double z) {
        return new Vec3(-x * PX, -y * PX, z * PX);
    }

    public static void build(Character character) {
        // a character builds its own body once. asking twice is a caller that did not know it
        // was already done, not a request for a second head
        if (character.child(HITBOX) != null) return;
        for (Limb limb : BODY) {
            Instances.create(Classes.PART, character, limb.name(), part -> {
                part.size = limb.size();
                part.cframe = CFrame.at(limb.pivot());
                // our pivot points from the middle of the box back to where it turns
                part.pivot = limb.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
            });
            shell(character, limb);
            grip(character, limb);
        }
        Instances.create(Classes.PART, character, HITBOX, part -> {
            part.size = Vec3.ONE;
            part.color = Color.WHITE;
            part.collides = false;
            part.anchored = true;
        });

        for (Limb wing : WING) {
            Instances.create(Classes.PART, character, wing.name(), part -> {
                part.size = wing.size();
                part.cframe = CFrame.at(wing.pivot());
                part.pivot = wing.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
            });
        }

        if (character.child("torso") instanceof Part torso) {
            Instances.create(Classes.CAPE, torso, CAPE, part -> {
                part.size = CAPE_BOX.size();
                part.pivot = CAPE_BOX.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
            });
        }

        Instances.create(Classes.WINGS, character, WING_SET);

        Instance joints = Instances.create(Classes.FOLDER, character, JOINTS);
        // the body's own, hanging off nothing: its transform is the tilt the whole body takes,
        // and every limb joint is composed through it
        Instances.create(Classes.JOINT, joints, ROOT, joint -> joint.part0 = character);
        if (character.child("torso") instanceof Part torso && torso.child(CAPE) != null) {
            Instances.create(Classes.JOINT, joints, CAPE, joint -> {
                joint.part0 = torso;
                joint.part1 = torso.child(CAPE);
            });
        }
        for (Limb wing : WING) {
            Instances.create(Classes.JOINT, joints, wing.name(), joint -> {
                joint.part0 = character;
                joint.part1 = character.child(wing.name());
            });
        }
        for (Limb limb : BODY) {
            Instances.create(Classes.JOINT, joints, limb.name(), joint -> {
                joint.part0 = character;
                joint.part1 = character.child(limb.name());
            });
        }
        apply(character);
    }

    // every body follows its own character's shape, once a tick, on whichever side is ticking
    //
    // scale, radius, height and display each change what the rig is, and a place on the server, a
    // place on the client or the engine can all write one. hanging the rebuild off the single
    // write that happened to be a bound player's profile change left two bodies wrong: a statue
    // that could not be resized at all, and a body scaled by the client with its joints moved to
    // the new size while its boxes kept the old one, which is a body coming apart at the joints
    //
    // a write that changes nothing is already a no-op, so a tick where no shape moved costs six
    // comparisons and touches nothing
    public static void follow(InstanceTree tree) {
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character) apply(character);
        }
    }

    // sizes are recomputed rather than scaled in place, so a place that writes scale twice gets
    // the same body both times instead of one that grew twice
    public static void apply(Character character) {
        double s = character.scale;
        boolean shown = character.display == CharacterDisplay.MODEL;

        for (Limb limb : BODY) {
            if (!(character.child(limb.name()) instanceof Part part)) continue;
            // the limb's own scale, over the body's. a joint that holds nothing bigger than it
            // was leaves this at one and the body is the size the model builds it
            Vec3 own = joint(character, limb.name()) instanceof Joint hinge ? hinge.scale : Vec3.ONE;
            Vec3 grown = limb.size().mul(s).mul(own);

            Instances.setObj(part, SIZE, grown);
            // the box grows away from its joint rather than about its own middle, so the offset
            // back to the joint grows with it and the limb stays joined where it was
            Instances.setObj(part, PIVOT, limb.box().neg().mul(s).mul(own));
            Instances.setBool(part, VISIBLE, shown);
            // where the joint stands, which is the engine's half of it. the turn at it is the
            // place's half and is never touched from here
            if (joint(character, limb.name()) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(limb.pivot().mul(s)));
            }

            if (part.child(GRIP) instanceof Spatial hand) {
                Instances.setObj(hand, GRIP_FRAME, hold(limb, character));
            }

            if (part.child(OVERLAY) instanceof Part over) {
                double shell = limb.shell() * 2;
                Instances.setObj(over, SIZE,
                        limb.size().add(new Vec3(shell, shell, shell)).mul(s).mul(own));
                // its visibility is the place's. the engine used to write it here every tick, so
                // a place that took a hat off had it put back on before the frame was drawn
            }
        }

        // a rig that has settled leaves the body where it says it is, rather than where it was
        // before the joints moved
        Joints.apply(character);

        if (character.child("torso") instanceof Part torso
                && torso.child(CAPE) instanceof Part cape) {
            Instances.setObj(cape, SIZE, CAPE_BOX.size().mul(s));
            Instances.setObj(cape, PIVOT, CAPE_BOX.box().neg().mul(s));
            // a cape and a pair of wings share a back, and the wings win. whether one is worn
            // at all is the cape's own business, so this only ever takes it away
            if (!shown || worn(character)) Instances.setBool(cape, VISIBLE, false);
            if (joint(character, CAPE) instanceof Joint hinge) {
                // the joint lives inside the torso's frame, whose origin is the middle of its
                // box rather than the shoulder it turns at. so the fold back comes first
                Instances.setObj(hinge, C0, CFrame.at(
                        BODY[1].box().neg().add(CAPE_BOX.pivot().sub(BODY[1].pivot())).mul(s)));
            }
        }

        for (Limb wing : WING) {
            if (!(character.child(wing.name()) instanceof Part part)) continue;
            // grown by a texel on every side, and the rect it is cut from is not
            double grown = wing.shell() * 2;
            Instances.setObj(part, SIZE,
                    wing.size().add(new Vec3(grown, grown, grown)).mul(s));
            Instances.setObj(part, PIVOT, wing.box().neg().mul(s));
            Instances.setBool(part, VISIBLE, shown && worn(character));
            if (joint(character, wing.name()) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(wing.pivot().mul(s)));
            }
        }

        if (character.child(HITBOX) instanceof Part box) {
            Instances.setObj(box, SIZE,
                    new Vec3(character.radius * 2, character.height, character.radius * 2));
            Instances.setObj(box, CFRAME, CFrame.at(0, character.height * 0.5, 0));
            Instances.setBool(box, VISIBLE, character.display == CharacterDisplay.HITBOX);
        }
    }

    // the shell sits on the part, not on the character: it inherits the swing, the pivot and the
    // scale of whatever it covers
    // the game's own hand placement, converted once like every other number here
    //
    // it states it as: take the arm's frame, turn a quarter back and a half about, then step out
    // by one, two and ten sixteenths. the step happens after the turns, so it is in the turned
    // frame, and the whole of it hangs off the arm's joint rather than the middle of its box --
    // which is why the fold back by the box offset is the first thing in it
    private static CFrame hold(Limb limb, Character character) {
        double side = "rightArm".equals(limb.name()) ? 1 : -1;
        // a slim arm is a texel narrower, and the game slides the hand half a texel inward to
        // follow it. nothing about the arm itself moves
        double slim = character.slim ? -side * 0.5 * PX : 0;
        Vec3 back = limb.box().neg().mul(character.scale).add(new Vec3(slim, 0, 0));

        Quat turn = Quat.axisAngle(new Vec3(1, 0, 0), Math.PI / 2)
                .mul(Quat.axisAngle(Vec3.UP, Math.PI));
        Vec3 out = new Vec3(-side * PX, -2 * PX, -10 * PX).mul(character.scale);
        return new CFrame(back, turn).mul(CFrame.at(out));
    }

    private static void grip(Character character, Limb limb) {
        if (!"rightArm".equals(limb.name()) && !"leftArm".equals(limb.name())) return;
        if (!(character.child(limb.name()) instanceof Part arm)) return;
        Instances.create(Classes.SPATIAL, arm, GRIP, hand -> hand.cframe = hold(limb, character));
    }

    private static void shell(Character character, Limb limb) {
        if (!(character.child(limb.name()) instanceof Part part)) return;
        Instances.create(Classes.PART, part, OVERLAY, over -> {
            over.size = part.size;
            over.color = Color.WHITE;
            over.collides = false;
            over.anchored = true;
        });
    }
}
