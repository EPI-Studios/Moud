package com.meekdev.moud.core.instance;

// a named group of parts and bodies. its name is the instance's name, and a part joins it by writing
// that name in collisionGroup. groups collide unless one of them lists the other in ignores
public final class CollisionGroup extends Instance {

    // comma separated names of groups this one passes through, itself included if it names itself
    public String ignores = "";
}
