package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Prop;

final class TestClasses {

    static final ClassDef<Thing> THING = ClassDef.of("Thing", null, Thing.class, Thing::new);
    static final ClassDef<Gadget> GADGET = ClassDef.of("Gadget", THING, Gadget.class, Gadget::new);

    static class Thing extends Instance {
        public boolean enabled = true;
        @Prop(replicated = false) public double localOnly;
    }

    static final class Gadget extends Thing {
        @Prop(min = 0, max = 1) public double charge;
        public int count = 3;
    }

    private TestClasses() {}
}
