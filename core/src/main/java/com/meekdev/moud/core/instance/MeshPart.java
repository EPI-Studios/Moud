package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a part drawn as a model rather than a box
//
// the model is stretched to fill size, the way a box is, so moving, scaling and colliding with it are
// all the part's own. what it collides as is still the box: a mesh is how it looks, not its shape
public class MeshPart extends Part {

    // the model: a place file like res://models/crate.glb, or a resource like mymod:models/crate.bbmodel.
    // gltf, glb, obj and bbmodel
    @Prop(asset = true) public String meshId = "";

    // the clip playing, by the name the file gives it. empty plays nothing and leaves the rest pose
    public String animation = "";

    @Prop(min = 0) public double animationSpeed = 1.0;

    public boolean animationLooped = true;
}
