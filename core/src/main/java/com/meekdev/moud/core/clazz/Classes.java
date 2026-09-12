package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Folder;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Spatial;
import java.util.List;

public final class Classes {

    public static final ClassDef<Folder> FOLDER = ClassDef.of("Folder", null, Folder.class, Folder::new);
    public static final ClassDef<Spatial> SPATIAL = ClassDef.of("Spatial", null, Spatial.class, Spatial::new);
    public static final ClassDef<Part> PART = ClassDef.of("Part", SPATIAL, Part.class, Part::new);
    public static final ClassDef<Character> CHARACTER =
            ClassDef.of("Character", SPATIAL, Character.class, Character::new);
    public static final ClassDef<Camera> CAMERA = ClassDef.of("Camera", SPATIAL, Camera.class, Camera::new);
    // not a spatial: a joint is not somewhere, it is how two things are held together
    public static final ClassDef<Joint> JOINT = ClassDef.of("Joint", null, Joint.class, Joint::new);

    private Classes() {}

    public static ClassRegistry registry() {
        return registry(List.of());
    }

    // the engine's own classes, then everyone else's
    //
    // the order matters for nothing on the wire -- an instance crosses as its class name, not an
    // index -- but it matters for a collision: an addon that names a class Part is told so, by an
    // error that names the addon
    public static ClassRegistry registry(Iterable<Addon> addons) {
        ClassRegistry r = new ClassRegistry();
        r.register(FOLDER);
        r.register(SPATIAL);
        r.register(PART);
        r.register(CHARACTER);
        r.register(CAMERA);
        r.register(JOINT);
        for (Addon addon : addons) {
            try {
                addon.classes(r);
            } catch (RuntimeException failed) {
                throw new IllegalStateException(
                        "addon '" + addon.id() + "' failed to register its classes", failed);
            }
        }
        return r;
    }
}
