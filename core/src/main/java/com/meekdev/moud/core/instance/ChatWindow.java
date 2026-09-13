package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// how the chat looks and whether it is there at all. the first one in the tree is the one used, and
// a place without one gets the game's chat as the player set it up
public final class ChatWindow extends Instance {

    // whether messages are drawn
    public boolean enabled = true;

    // whether a player can open the chat and type
    public boolean inputEnabled = true;

    // moves the whole window, in gui pixels. up is negative, the way the screen counts
    public double offsetX;
    public double offsetY;

    // in gui pixels
    @Prop(min = 40) public double width = 320;
    @Prop(min = 20) public double height = 90;
    @Prop(min = 20) public double focusedHeight = 180;

    @Prop(min = 0.1, max = 4) public double scale = 1;

    @Prop(min = 0, max = 1) public double lineSpacing;

    @Prop(min = 0, max = 1) public double textTransparency;

    public Color backgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.5;

    // one of the game's fonts, like minecraft:uniform. empty is the default font
    @Prop(asset = true) public String font = "";
}
