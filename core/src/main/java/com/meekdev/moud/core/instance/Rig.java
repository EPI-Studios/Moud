package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;

public final class Rig {

    public static final String HITBOX = "hitbox";

    public static final String JOINTS = "joints";
    public static final String APPEARANCE = "appearance";

    public static final String ROOT = "root";

    public static final String OVERLAY = "overlay";

    public static final String GRIP = "grip";

    public static final String HAT = "hat";

    public static final String BACK = "back";

    public static final String[] WINGS = {"rightWing", "leftWing"};

    public static final String[] SPIN = {"spinInner", "spinOuter"};

    public static final String[] EARS = {"rightEar", "leftEar"};

    public static final String CAPE = "cape";

    public static final String WING_SET = "wings";

    public static final String HUMANOID = "humanoid";

    public static final String ANIMATOR = "animator";

    public static final String ARMOUR = "armour";

    public static final String[] WORN_HEAD = {"wornHead", "wornHeadLayer"};

    private static final double HEAD_WORN = 1.1875;

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

    public static final String SHEET_CAPE = "cape";
    public static final String SHEET_ELYTRA = "elytra";
    public static final String SHEET_RIPTIDE = "riptide";
    public static final String SHEET_ARMOUR = "armour:";

    private static final double PX = 1.0 / 16.0;

    private static final double STANDING = 24.0;

    private static final PropertyDef SIZE = Classes.PART.property("size");
    private static final PropertyDef CFRAME = Classes.PART.property("cframe");
    private static final PropertyDef PIVOT = Classes.PART.property("pivot");
    private static final PropertyDef VISIBLE = Classes.PART.property("visible");
    private static final PropertyDef C0 = Classes.JOINT.property("c0");
    private static final PropertyDef GRIP_FRAME = Classes.SPATIAL.property("cframe");
    private static final PropertyDef TEXELS = Classes.LIMB.property("texels");

    private record Shape(String name, Vector3 pivot, Vector3 box, Vector3 size, double shell,
                         Vector3 texels, double u, double v, double shellU, double shellV) {}

    private static final Shape[] WING = {
            limb("rightWing", -5, 0, -2, 0, 0, 0, 10, 20, 2, 1.0, 22, 0),
            limb("leftWing", 5, 0, -2, -10, 0, 0, 10, 20, 2, 1.0, 22, 0),
    };

    private static final Shape CAPE_BOX = limb("cape", 0, 0, -2, -5, 0, -1, 10, 16, 1, 0, 0, 0);

    private static final Shape[] SPINS = {
            limb("spinInner", 0, 0, 0, -8, -9.6, -8, 16, 32, 16, 0, 0, 0),
            limb("spinOuter", 0, 0, 0, -8, 0, -8, 16, 32, 16, 0, 0, 0),
    };

    private static final double[] SPIN_SCALE = {0.75, 1.5};

    private static final Shape[] EAR = {
            limb("rightEar", 6, -6, 0, -3, -6, -1, 6, 6, 1, 1.0, 24, 0),
            limb("leftEar", -6, -6, 0, -3, -6, -1, 6, 6, 1, 1.0, 24, 0),
    };

    private static final Shape[] BODY = {
            limb("head", 0, 0, 0, -4, -8, -4, 8, 8, 8, 0.5, 0, 0, 32, 0),
            limb("torso", 0, 0, 0, -4, 0, -2, 8, 12, 4, 0.25, 16, 16, 16, 32),
            limb("rightArm", -5, 2, 0, -3, -2, -2, 4, 12, 4, 0.25, 40, 16, 40, 32),
            limb("leftArm", 5, 2, 0, -1, -2, -2, 4, 12, 4, 0.25, 32, 48, 48, 48),
            limb("rightLeg", -1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25, 0, 16, 0, 32),
            limb("leftLeg", 1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25, 16, 48, 0, 48),
    };

    public static String[] limbs() {
        String[] names = new String[BODY.length];
        for (int n = 0; n < BODY.length; n++) names[n] = BODY[n].name();
        return names;
    }

    private Rig() {}

    private static Shape limb(String name, double px, double py, double pz,
                             double bx, double by, double bz, double w, double h, double d,
                             double shell, double u, double v) {
        return limb(name, px, py, pz, bx, by, bz, w, h, d, shell, u, v, 0, 0);
    }

    private static Shape limb(String name, double px, double py, double pz,
                             double bx, double by, double bz, double w, double h, double d,
                             double shell, double u, double v, double shellU, double shellV) {
        Vector3 pivot = new Vector3(-px * PX, (STANDING - py) * PX, pz * PX);
        Vector3 centre = new Vector3(-(bx + w * 0.5) * PX, -(by + h * 0.5) * PX, (bz + d * 0.5) * PX);
        return new Shape(name, pivot, centre, new Vector3(w * PX, h * PX, d * PX), shell * PX,
                new Vector3(w, h, d), u, v, shellU, shellV);
    }

    public static Armour armour(Character character) {
        return character.child(ARMOUR) instanceof Armour worn ? worn : null;
    }

    public static String slot(Armour worn, String slot) {
        if (worn == null) return "";
        return switch (slot) {
            case "head" -> worn.head;
            case "chest" -> worn.chest;
            case "legs" -> worn.legs;
            default -> worn.feet;
        };
    }

    private static double arm(String name) {
        if ("rightArm".equals(name)) return 1;
        return "leftArm".equals(name) ? -1 : 0;
    }

    private static Shape shapeOf(String name) {
        for (Shape limb : BODY) {
            if (limb.name().equals(name)) return limb;
        }
        return null;
    }

    public static Appearance appearance(Character character) {
        return character.child(APPEARANCE) instanceof Appearance look ? look : null;
    }

    public static Humanoid humanoid(Character character) {
        return character.child(HUMANOID) instanceof Humanoid living ? living : null;
    }

    public static Wings wings(Character character) {
        return character.child(WING_SET) instanceof Wings pair ? pair : null;
    }

    private static boolean worn(Character character) {
        Wings pair = wings(character);
        return pair != null && pair.worn;
    }

    public static Instance joint(Character character, String name) {
        return character.child(JOINTS) instanceof Instance joints ? joints.child(name) : null;
    }

    public static Vector3 pivot(String name, double scale) {
        for (Shape limb : BODY) {
            if (limb.name().equals(name)) return limb.pivot().mul(scale);
        }
        return Vector3.ZERO;
    }

    public static Vector3 offset(double x, double y, double z) {
        return new Vector3(-x * PX, -y * PX, z * PX);
    }

    private static double swing(String name) {
        return switch (name) {
            case "rightArm", "leftArm" -> 1.0;
            case "rightLeg", "leftLeg" -> 1.4;
            default -> 0;
        };
    }

    private static double swingPhase(String name) {
        return switch (name) {
            case "rightArm", "leftLeg" -> 0.5;
            default -> 0;
        };
    }

    public static void build(Character character) {
        if (character.child(HITBOX) != null) return;
        for (Shape limb : BODY) {
            Instances.create(Classes.LIMB, character, limb.name(), part -> {
                part.size = limb.size();
                part.cframe = CFrame.at(limb.pivot());
                part.pivot = limb.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.u = limb.u();
                part.v = limb.v();
                part.texels = limb.texels();
                part.swing = swing(limb.name());
                part.swingPhase = swingPhase(limb.name());
            });
            shell(character, limb);
            grip(character, limb);
            if ("head".equals(limb.name())) attach(character, limb, HAT, Vector3.ZERO);
            if ("torso".equals(limb.name())) {
                attach(character, limb, BACK, CAPE_BOX.pivot().sub(limb.pivot()));
            }
        }
        Instances.create(Classes.PART, character, HITBOX, part -> {
            part.size = Vector3.ONE;
            part.color = Color.WHITE;
            part.collides = false;
            part.anchored = true;
        });

        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (Shape ear : EAR) {
                Instances.create(Classes.LIMB, point, ear.name(), part -> {
                    part.color = Color.WHITE;
                    part.collides = false;
                    part.anchored = true;
                    part.visible = false;
                    part.u = ear.u();
                    part.v = ear.v();
                    part.texels = ear.texels();
                    part.cutout = true;
                });
            }
        }

        for (int n = 0; n < SPINS.length; n++) {
            Shape shell = SPINS[n];
            double at = SPIN_SCALE[n];
            Instances.create(Classes.LIMB, character, shell.name(), part -> {
                part.size = shell.size().mul(at);
                part.cframe = CFrame.at(shell.pivot());
                part.pivot = shell.box().neg().mul(at);
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
                part.u = shell.u();
                part.v = shell.v();
                part.texels = shell.texels();
                part.sheet = SHEET_RIPTIDE;
                part.cutout = true;
            });
        }

        for (Shape wing : WING) {
            Instances.create(Classes.LIMB, character, wing.name(), part -> {
                part.size = wing.size();
                part.cframe = CFrame.at(wing.pivot());
                part.pivot = wing.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
                part.u = wing.u();
                part.v = wing.v();
                part.texels = wing.texels();
                part.sheetHeight = 32;
                part.mirrored = WINGS[0].equals(wing.name());
                part.sheet = SHEET_ELYTRA;
                part.cutout = true;
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
                part.u = CAPE_BOX.u();
                part.v = CAPE_BOX.v();
                part.texels = CAPE_BOX.texels();
                part.sheetHeight = 32;
                part.sheet = SHEET_CAPE;
                part.cutout = true;
            });
        }

        Instances.create(Classes.HUMANOID, character, HUMANOID);
        Instances.create(Classes.ANIMATOR, character, ANIMATOR);
        Instances.create(Classes.ARMOUR, character, ARMOUR);
        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (int n = 0; n < WORN_HEAD.length; n++) {
                boolean layer = n == 1;
                Instances.create(Classes.LIMB, point, WORN_HEAD[n], part -> {
                    part.color = Color.WHITE;
                    part.collides = false;
                    part.anchored = true;
                    part.visible = false;
                    part.u = layer ? 32 : 0;
                    part.v = 0;
                    part.texels = new Vector3(8, 8, 8);
                    part.sheet = SHEET_ARMOUR + "head";
                    part.cutout = true;
                });
            }
        }
        for (Plate plate : ARMOUR_PLATES) {
            if (!(character.child(plate.limb()) instanceof Part limb)) continue;
            Shape shape = shapeOf(plate.limb());
            if (shape == null) continue;
            Instances.create(Classes.LIMB, limb, plate.name(), part -> {
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
                part.visible = false;
                part.u = plate.u();
                part.v = plate.v();
                part.texels = shape.texels();
                part.sheetHeight = 32;
                part.mirrored = plate.mirrored();
                part.sheet = SHEET_ARMOUR + plate.slot();
                part.cutout = true;
            });
        }
        Instances.create(Classes.WINGS, character, WING_SET);
        Instances.create(Classes.APPEARANCE, character, APPEARANCE);

        Instance frame = Instances.create(Classes.ATTACHMENT, character, ROOT);

        Instance joints = Instances.create(Classes.FOLDER, character, JOINTS);
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
        for (Shape shell : SPINS) {
            Instances.create(Classes.MOTOR, joints, shell.name(), joint -> {
                joint.part0 = frame;
                joint.part1 = character.child(shell.name());
            });
        }
        for (Shape wing : WING) {
            Instances.create(Classes.MOTOR, joints, wing.name(), joint -> {
                joint.part0 = frame;
                joint.part1 = character.child(wing.name());
            });
        }
        for (Shape limb : BODY) {
            Instances.create(Classes.MOTOR, joints, limb.name(), joint -> {
                joint.part0 = frame;
                joint.part1 = character.child(limb.name());
            });
        }
        apply(character);
    }

    public static void follow(InstanceTree tree) {
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character) apply(character);
        }
    }

    public static void apply(Character character) {
        double s = character.scale;
        Appearance look = appearance(character);

        boolean slim = look != null && look.slim;
        for (Shape limb : BODY) {
            if (!(character.child(limb.name()) instanceof Part part)) continue;
            Vector3 own = joint(character, limb.name()) instanceof Joint hinge ? hinge.scale : Vector3.ONE;
            Vector3 grown = limb.size().mul(s).mul(own);

            double narrow = slim ? arm(limb.name()) : 0;
            double texel = narrow == 0 ? 0 : limb.size().x() * 0.25 * s * own.x();
            if (narrow != 0) grown = new Vector3(grown.x() - texel, grown.y(), grown.z());

            Instances.setObj(part, SIZE, grown);
            Instances.setObj(part, PIVOT, limb.box().neg().mul(s).mul(own)
                    .add(new Vector3(narrow * texel * 0.5, 0, 0)));
            if (part instanceof Limb cut) {
                Instances.setObj(cut, TEXELS, narrow == 0
                        ? limb.texels()
                        : new Vector3(limb.texels().x() - 1, limb.texels().y(), limb.texels().z()));
            }
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
                Vector3 wide = limb.size().add(new Vector3(shell, shell, shell)).mul(s).mul(own);
                Instances.setObj(over, SIZE,
                        narrow == 0 ? wide : new Vector3(wide.x() - texel, wide.y(), wide.z()));
                if (over instanceof Limb cut) {
                    Instances.setObj(cut, TEXELS, narrow == 0
                            ? limb.texels()
                            : new Vector3(limb.texels().x() - 1, limb.texels().y(), limb.texels().z()));
                }
            }
        }

        if (character.child(JOINTS) instanceof Instance joints) {
            for (Instance held : joints.children()) {
                if (held instanceof Joint hinge) hinge.compose();
            }
        }

        if (character.child("torso") instanceof Part torso
                && torso.child(CAPE) instanceof Part cape) {
            Instances.setObj(cape, SIZE, CAPE_BOX.size().mul(s));
            Instances.setObj(cape, PIVOT, CAPE_BOX.box().neg().mul(s));
            if (worn(character)) Instances.setBool(cape, VISIBLE, false);
            if (joint(character, CAPE) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(
                        BODY[1].box().neg().add(CAPE_BOX.pivot().sub(BODY[1].pivot())).mul(s)));
            }
        }

        for (Shape wing : WING) {
            if (!(character.child(wing.name()) instanceof Part part)) continue;
            double grown = wing.shell() * 2;
            Instances.setObj(part, SIZE,
                    wing.size().add(new Vector3(grown, grown, grown)).mul(s));
            Instances.setObj(part, PIVOT, wing.box().neg().mul(s));
            Instances.setBool(part, VISIBLE, worn(character));
            if (joint(character, wing.name()) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(wing.pivot().mul(s)));
            }
        }

        for (int n = 0; n < SPINS.length; n++) {
            Shape shell = SPINS[n];
            if (!(character.child(shell.name()) instanceof Part part)) continue;
            double at = SPIN_SCALE[n] * s;
            Instances.setObj(part, SIZE, shell.size().mul(at));
            Instances.setObj(part, PIVOT, shell.box().neg().mul(at));
            Instances.setBool(part, VISIBLE, character.spinning);
            if (joint(character, shell.name()) instanceof Joint hinge) {
                Instances.setObj(hinge, C0, CFrame.at(shell.pivot().mul(s)));
            }
        }

        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            for (Shape ear : EAR) {
                if (!(point.child(ear.name()) instanceof Part part)) continue;
                double out = ear.shell() * 2;
                Instances.setObj(part, SIZE,
                        ear.size().add(new Vector3(out, out, out)).mul(s));
                Instances.setObj(part, CFRAME,
                        CFrame.at(ear.pivot().sub(BODY[0].pivot()).mul(s)));
                Instances.setObj(part, PIVOT, ear.box().neg().mul(s));
                Instances.setBool(part, VISIBLE, look != null && look.ears);
            }
        }

        Armour worn = armour(character);
        if (character.child("head") instanceof Part head
                && head.child(HAT) instanceof Spatial point) {
            boolean wearing = worn != null && !worn.hat.isEmpty();
            for (int n = 0; n < WORN_HEAD.length; n++) {
                if (!(point.child(WORN_HEAD[n]) instanceof Part part)) continue;
                double out = n == 0 ? 0 : 0.25 * 2 * PX;
                double at = HEAD_WORN * s;
                Instances.setObj(part, SIZE,
                        new Vector3(8 * PX + out, 8 * PX + out, 8 * PX + out).mul(at));
                Instances.setObj(part, PIVOT, new Vector3(0, -4 * PX, 0).mul(at));
                Instances.setBool(part, VISIBLE, wearing && (n == 0 || worn.hatLayered));
            }
        }
        for (Plate plate : ARMOUR_PLATES) {
            if (!(character.child(plate.limb()) instanceof Part limb)) continue;
            if (!(limb.child(plate.name()) instanceof Part plated)) continue;
            Shape shape = shapeOf(plate.limb());
            if (shape == null) continue;

            Vector3 own = joint(character, plate.limb()) instanceof Joint hinge ? hinge.scale : Vector3.ONE;
            double out = plate.grow() * 2 * PX * s;
            Instances.setObj(plated, SIZE,
                    shape.size().mul(s).mul(own).add(new Vector3(out, out, out)));
            boolean taken = "head".equals(plate.slot()) && worn != null && !worn.hat.isEmpty();
            Instances.setBool(plated, VISIBLE, !taken && !slot(worn, plate.slot()).isEmpty());
        }

        if (character.child(HITBOX) instanceof Part box) {
            Instances.setObj(box, SIZE,
                    new Vector3(character.radius * 2, character.height, character.radius * 2));
            Instances.setObj(box, CFRAME, CFrame.at(0, character.height * 0.5, 0));
            Instances.setBool(box, VISIBLE, look != null && look.display == CharacterDisplay.HITBOX);
        }
    }

    private static CFrame hold(Shape limb, Character character) {
        double side = "rightArm".equals(limb.name()) ? 1 : -1;
        Vector3 back = limb.box().neg().mul(character.scale);

        Quat turn = Quat.axisAngle(new Vector3(1, 0, 0), Math.PI / 2)
                .mul(Quat.axisAngle(Vector3.UP, Math.PI));
        Vector3 out = new Vector3(-side * PX, -2 * PX, -10 * PX).mul(character.scale);
        return new CFrame(back, turn).mul(CFrame.at(out));
    }

    private static void attach(Character character, Shape limb, String name, Vector3 from) {
        if (!(character.child(limb.name()) instanceof Part part)) return;
        Instances.create(Classes.ATTACHMENT, part, name,
                point -> point.cframe = CFrame.at(limb.box().neg().add(from)));
    }

    private static void grip(Character character, Shape limb) {
        if (!"rightArm".equals(limb.name()) && !"leftArm".equals(limb.name())) return;
        if (!(character.child(limb.name()) instanceof Part arm)) return;
        Instances.create(Classes.ATTACHMENT, arm, GRIP, hand -> hand.cframe = hold(limb, character));
    }

    private static void shell(Character character, Shape limb) {
        if (!(character.child(limb.name()) instanceof Part part)) return;
        Instances.create(Classes.LIMB, part, OVERLAY, over -> {
            over.size = part.size;
            over.color = Color.WHITE;
            over.collides = false;
            over.anchored = true;
            over.u = limb.shellU();
            over.v = limb.shellV();
            over.texels = limb.texels();
            over.cutout = true;
        });
    }
}
