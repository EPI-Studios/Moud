package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a screen effect written by the place
//
// the shader is a fragment shader file, like res://post/scanlines.glsl. it reads the picture through
// sceneColor(uv) and depth through sceneDepth(uv), linearDepth(uv) and worldPosition(uv), and writes
// FragColor. Time, ScreenSize, Intensity, CameraPosition, ViewProj, InvViewProj and PrevViewProj are
// there too. every NumberValue, BoolValue and Vector3Value child is a uniform of the same name, so a
// place tunes its shader by writing values
public final class PostShader extends ScreenEffect {

    @Prop(asset = true) public String shader = "";
}
