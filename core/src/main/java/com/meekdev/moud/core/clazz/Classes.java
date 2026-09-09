package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Folder;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Spatial;

public final class Classes {

    public static final ClassDef<Folder> FOLDER = ClassDef.of("Folder", null, Folder.class, Folder::new);
    public static final ClassDef<Spatial> SPATIAL = ClassDef.of("Spatial", null, Spatial.class, Spatial::new);
    public static final ClassDef<Part> PART = ClassDef.of("Part", SPATIAL, Part.class, Part::new);
    public static final ClassDef<Character> CHARACTER =
            ClassDef.of("Character", SPATIAL, Character.class, Character::new);

    private Classes() {}

    public static ClassRegistry registry() {
        ClassRegistry r = new ClassRegistry();
        r.register(FOLDER);
        r.register(SPATIAL);
        r.register(PART);
        r.register(CHARACTER);
        return r;
    }
}
