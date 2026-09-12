package com.meekdev.moud.core.instance;

// a joint an animator is allowed to pose
//
// that permission is the whole difference from a plain joint, and it is worth a class because of
// what it lets go of: a body ragdolls by having its motors swapped for sockets, and the animator
// then writes nothing at all, because there is no motor left for it to find. no stage has to
// remember that the body is limp
public final class Motor extends Joint {
}
