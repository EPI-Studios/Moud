package com.meekdev.moud.core.instance;

// what poses a body, and where more than one thing may do it at once
//
// the engine's own walk is the floor: it writes every joint every tick. a track laid over it takes
// the joints it names and leaves the rest alone, so a wave is a wave and not a whole body pose
// somebody had to author around the walk
public final class Animator extends Instance {
}
