package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// drawn over the screen of every client that holds it. made on a client, it is that client's own
public final class ScreenGui extends Instance {

    public boolean enabled = true;

    // higher is drawn over lower
    public int displayOrder;

    // the font every text inside is drawn in unless it names its own
    @Prop(asset = true) public String font = "";
}
