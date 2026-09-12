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

    // where a hat, a helmet or a worn head sits. the game anchors one at the neck and lets it
    // reach up over the skull, so this is the head's own joint rather than the top of it
    public static final String HAT = "hat";

    // where a cape, a pair of wings or a pack rides
    public static final String BACK = "back";

    // a pair of wings on the back. they are not limbs: the model states them from the body's own
    // root and steps them two texels back, and they are drawn off a sheet of their own
    public static final String[] WINGS = {"rightWing", "leftWing"};

    // the two shells a riptide throws up around a body
    public static final String[] SPIN = {"spinInner", "spinOuter"};

    // the pair a name nobody says out loud wears
    public static final String[] EARS = {"rightEar", "leftEar"};

    // hung off the back of the torso rather than off the body, so it leans with a crouch and
    // twists with a swing without being told to
    public static final String CAPE = "cape";

    // the pair's own state, beside the two boxes that draw it
    public static final String WING_SET = "wings";

    // the living half of a body
    public static final String HUMANOID = "humanoid";

    // where the tracks that pose it live
    public static final String ANIMATOR = "animator";

    // what it is wearing
    public static final String ARMOUR = "armour";

    // a worn head, and the second layer some of them carry
    public static final String[] WORN_HEAD = {"wornHead", "wornHeadLayer"};

    // the game draws one at a shade over full size, which is what makes it sit around a head
    // rather than inside it
    private static final double HEAD_WORN = 1.1875;

    // the ten boxes four pieces of armour are made of, named for the limb they cover and the slot
    // they belong to
    //
    // the grows are not decoration: leggings use half a texel where the other three use a whole
    // one, and boots take a tenth off the legs. that is exactly what stops each piece coming
    // through the one under it, and getting it wrong is armour that z fights with itself
    public record Plate(String limb, String name, String slot, float u, float v,
                        double grow, boolean mirrored) {}

    public static final Plate[] ARMOUR_PLATES = {
            new Plate("head", "helmet", "head", 0, 0, 1.0, false),
            new Plate("head", "helmetHat", "head", 32, 0, 1.5, false),
            new Plate("torso", "chestplate", "chest", 16, 16, 1.0, false),
            new Plate("rightArm", "chestplate", "chest", 40, 16, 1.0, false),
            new Plate("leftArm", "chestplate", "chest", 40, 16, 1.0, true),
            new Plate("torso", "leggings", "legs", 16, 16, 0.5, false),
            new Plate("rightLeg", "leggings", "legs", 0, 16, 0.4, false),
            new Plate("leftLeg", "leggings", "legs", 0, 16, 0.4, true),
            new Plate("rightLeg", "boots", "feet", 0, 16, 0.9, false),
            new Plate("leftLeg", "boots", "feet", 0, 16, 0.9, true),
    };

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

    // sixteen by thirty two by sixteen, twice, at three quarters and one and a half. the two
    // spin at different rates, which is the whole of the effect
    private static final Limb[] SPINS = {
            limb("spinInner", 0, 0, 0, -8, -9.6, -8, 16, 32, 16, 0),
            limb("spinOuter", 0, 0, 0, -8, 0, -8, 16, 32, 16, 0),
    };

    private static final double[] SPIN_SCALE = {0.75, 1.5};

    // six by six by one, grown a whole texel on every side, hung off the head's own joint. the
    // rect stays sized for the ungrown box, which is what a grow always does
    private static final Limb[] EAR = {
            limb("rightEar", 6, -6, 0, -3, -6, -1, 6, 6, 1, 1.0),
            limb("leftEar", -6, -6, 0, -3, -6, -1, 6, 6, 1, 1.0),
    };

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
    // what this body is wearing, or nothing if something took it away
    public static Armour armour(Character character) {
        return character.child(ARMOUR) instanceof Armour worn ? worn : null;
    }

    // the sheet a slot is wearing, by name rather than by four branches at every call site
    public static String slot(Armour worn, String slot) {
        if (worn == null) return "";
        return switch (slot) {
            case "head" -> worn.head;
            case "chest" -> worn.chest;
            case "legs" -> worn.legs;
            default -> worn.feet;
        };
    }

    private static Limb shapeOf(String name) {
        for (Limb limb : BODY) {
            if (limb.name().equals(name)) return limb;
        }
        return null;
    }

    // the living half, or nothing if something took it away
    public static Humanoid humanoid(Character character) {
        return character.child(HUMANOID) instanceof Humanoid living ? living : null;
    }

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
            if ("head".equals(limb.name())) attach(character, limb, HAT, Vec3.ZERO);
            if ("torso".equals(limb.name())) {
                attach(character, limb, BACK, CAPE_BOX.pivot().sub(limb.pivot()));
            }
        }
        Instances.create(Classes.PART, character, HITBOX, part -> {
            part.size = Vec3.ONE;
            part.color = Color.WHITE;
            part.collides = false;
            part.anchored = true;
        });

        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (Limb ear : EAR) {
                Instances.create(Classes.PART, point, ear.name(), part -> {
                    part.color = Color.WHITE;
                    part.collides = false;
                    part.anchored = true;
                    part.visible = false;
                });
            }
        }

        for (int n = 0; n < SPINS.length; n++) {
            Limb shell = SPINS[n];
            double at = SPIN_SCALE[n];
            Instances.create(Classes.PART, character, shell.name(), part -> {
                part.size = shell.size().mul(at);
                part.cframe = CFrame.at(shell.pivot());
                part.pivot = shell.box().neg().mul(at);
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
            });
        }

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

        Instances.create(Classes.HUMANOID, character, HUMANOID);
        Instances.create(Classes.ANIMATOR, character, ANIMATOR);
        Instances.create(Classes.ARMOUR, character, ARMOUR);
        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (String name : WORN_HEAD) {
                Instances.create(Classes.PART, point, name, part -> {
                    part.color = Color.WHITE;
                    part.collides = false;
                    part.anchored = true;
                    part.visible = false;
                });
            }
        }
        for (Plate plate : ARMOUR_PLATES) {
            if (!(character.child(plate.limb()) instanceof Part limb)) continue;
            Instances.create(Classes.PART, limb, plate.name(), part -> {
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
            });
        }
        Instances.create(Classes.WINGS, character, WING_SET);

        // the frame the whole body hangs from. it is a real point on the character rather than a
        // prefix the composer remembers to apply: every limb joint holds onto this, so the body
        // tilting is one joint turning and the limbs know nothing about it
        Instance frame = Instances.create(Classes.ATTACHMENT, character, ROOT);

        Instance joints = Instances.create(Classes.FOLDER, character, JOINTS);
        // first, because a stage visits in the order things entered the tree and everything below
        // is composed through what this one leaves
        Instances.create(Classes.MOTOR, joints, ROOT, joint -> {
            joint.part0 = character;
            joint.part1 = frame;
        });
        if (character.child("torso") instanceof Part torso && torso.child(CAPE) != null) {
            Instances.create(Classes.MOTOR, joints, CAPE, joint -> {
                joint.part0 = torso;
                joint.part1 = torso.child(CAPE);
            });
        }
        for (Limb shell : SPINS) {
            Instances.create(Classes.MOTOR, joints, shell.name(), joint -> {
                joint.part0 = frame;
                joint.part1 = character.child(shell.name());
            });
        }
        for (Limb wing : WING) {
            Instances.create(Classes.MOTOR, joints, wing.name(), joint -> {
                joint.part0 = frame;
                joint.part1 = character.child(wing.name());
            });
        }
        for (Limb limb : BODY) {
            Instances.create(Classes.MOTOR, joints, limb.name(), joint -> {
                joint.part0 = frame;
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
            if (part.child(HAT) instanceof Spatial hat) {
                Instances.setObj(hat, GRIP_FRAME, CFrame.at(limb.box().neg().mul(s)));
            }
            if (part.child(BACK) instanceof Spatial back) {
                Instances.setObj(back, GRIP_FRAME, CFrame.at(
                        limb.box().neg().add(CAPE_BOX.pivot().sub(limb.pivot())).mul(s)));
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
        // before the joints moved. asked in the order they were made, so the root frame is where
        // it belongs before anything hanging off it is composed through it
        if (character.child(JOINTS) instanceof Instance joints) {
            for (Instance held : joints.children()) {
                if (held instanceof Joint hinge) hinge.compose();
            }
        }

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

        for (int n = 0; n < SPINS.length; n++) {
            Limb shell = SPINS[n];
            if (!(character.child(shell.name()) instanceof Part part)) continue;
            double at = SPIN_SCALE[n] * s;
            Instances.setObj(part, SIZE, shell.size().mul(at));
            Instances.setObj(part, PIVOT, shell.box().neg().mul(at));
            Instances.setBool(part, VISIBLE, shown && character.spinning);
            if (joint(character, shell.name()) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(shell.pivot().mul(s)));
            }
        }

        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (Limb ear : EAR) {
                if (!(point.child(ear.name()) instanceof Part part)) continue;
                double out = ear.shell() * 2;
                Instances.setObj(part, SIZE,
                        ear.size().add(new Vec3(out, out, out)).mul(s));
                // measured from the head's own joint, which is what the point is
                Instances.setObj(part, CFRAME,
                        CFrame.at(ear.pivot().sub(BODY[0].pivot()).mul(s)));
                Instances.setObj(part, PIVOT, ear.box().neg().mul(s));
                Instances.setBool(part, VISIBLE, shown && character.ears);
            }
        }

        Armour worn = armour(character);
        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            boolean wearing = worn != null && !worn.hat.isEmpty();
            for (int n = 0; n < WORN_HEAD.length; n++) {
                if (!(point.child(WORN_HEAD[n]) instanceof Part part)) continue;
                // the second layer stands a quarter texel proud of the first, exactly as a hat
                // stands off a head
                double out = n == 0 ? 0 : 0.25 * 2 * PX;
                double at = HEAD_WORN * s;
                Instances.setObj(part, SIZE,
                        new Vec3(8 * PX + out, 8 * PX + out, 8 * PX + out).mul(at));
                // its box hangs four texels above the neck it is anchored at
                Instances.setObj(part, PIVOT, new Vec3(0, -4 * PX, 0).mul(at));
                Instances.setBool(part, VISIBLE,
                        shown && wearing && (n == 0 || worn.hatLayered));
            }
        }
        for (Plate plate : ARMOUR_PLATES) {
            if (!(character.child(plate.limb()) instanceof Part limb)) continue;
            if (!(limb.child(plate.name()) instanceof Part plated)) continue;
            Limb shape = shapeOf(plate.limb());
            if (shape == null) continue;

            double out = plate.grow() * 2 * PX;
            Instances.setObj(plated, SIZE,
                    shape.size().add(new Vec3(out, out, out)).mul(s));
            Instances.setObj(plated, PIVOT, shape.box().neg().mul(s));
            boolean taken = "head".equals(plate.slot()) && worn != null && !worn.hat.isEmpty();
            Instances.setBool(plated, VISIBLE,
                    shown && !taken && !slot(worn, plate.slot()).isEmpty());
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

    // a point on a limb, stated from that limb's own joint rather than from the middle of its
    // box, because a joint is what the game states everything from
    private static void attach(Character character, Limb limb, String name, Vec3 from) {
        if (!(character.child(limb.name()) instanceof Part part)) return;
        Instances.create(Classes.ATTACHMENT, part, name,
                point -> point.cframe = CFrame.at(limb.box().neg().add(from)));
    }

    private static void grip(Character character, Limb limb) {
        if (!"rightArm".equals(limb.name()) && !"leftArm".equals(limb.name())) return;
        if (!(character.child(limb.name()) instanceof Part arm)) return;
        Instances.create(Classes.ATTACHMENT, arm, GRIP, hand -> hand.cframe = hold(limb, character));
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
