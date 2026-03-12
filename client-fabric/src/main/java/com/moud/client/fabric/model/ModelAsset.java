package com.moud.client.fabric.model;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public final class ModelAsset {
    private final String name;
    private final List<BoneNode> rootBones;
    private final Map<String, BoneNode> bonesByUuid;
    private final Map<String, AnimationClip> animations;
    private final List<Identifier> textureIds;
    private final int resWidth;
    private final int resHeight;

    public ModelAsset(String name,
                      List<BoneNode> rootBones,
                      Map<String, BoneNode> bonesByUuid,
                      Map<String, AnimationClip> animations,
                      List<Identifier> textureIds,
                      int resWidth,
                      int resHeight) {
        this.name = name;
        this.rootBones = List.copyOf(rootBones);
        this.bonesByUuid = Map.copyOf(bonesByUuid);
        this.animations = Map.copyOf(animations);
        this.textureIds = List.copyOf(textureIds);
        this.resWidth = resWidth;
        this.resHeight = resHeight;
    }

    public String name()                          { return name; }
    public List<BoneNode> rootBones()             { return rootBones; }
    public Map<String, BoneNode> bonesByUuid()    { return bonesByUuid; }
    public Map<String, AnimationClip> animations(){ return animations; }
    public List<Identifier> textureIds()          { return textureIds; }
    public int resWidth()                         { return resWidth; }
    public int resHeight()                        { return resHeight; }
}
