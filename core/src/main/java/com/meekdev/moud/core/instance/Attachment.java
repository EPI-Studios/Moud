package com.meekdev.moud.core.instance;

// a named place on a body where something can be hung
//
// a frame and nothing else: no size, nothing drawn. anything parented to one is carried by it,
// and the hierarchy does the rest -- the swing, the crouch, the scale and the body's own tilt are
// already in the chain above it
//
// the body ships the ones the game itself defines, at the exact frames the game uses. a place is
// free to add its own anywhere
public final class Attachment extends Spatial {
}
