package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a shader text is drawn through where it says <shader=name>, the name being this instance's name
//
// the file is the body of a glsl function vec4 textColor(vec4 color, vec2 uv, vec2 glyph, float time),
// given the glyph's colour, where on the screen it is, where inside the glyph, and the time
public final class ChatTextShader extends Instance {

    @Prop(asset = true) public String shader = "";
}
