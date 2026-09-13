package com.meekdev.moud.core.instance;

// one player's membership of a channel, as a child of it
public final class TextSource extends Instance {

    // the player's id
    public String player = "";

    // whether they may speak here. a muted player still reads the channel
    public boolean canSend = true;
}
