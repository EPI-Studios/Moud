package com.meekdev.moud.core.instance;

// one player's membership of a channel, as a child of it
public final class TextSource extends Instance {

    // the player's id
    public String player = "";

    // the body that player is wearing, kept up to date by the server, so a hook never has to go looking
    public Instance body;

    // whether they may speak here. a muted player still reads the channel
    public boolean canSend = true;

    // which rich text tags this player's own messages keep, as names split by commas: "b,i,color", or
    // "*" for all of them. empty, the default, shows whatever they type exactly as typed. a player given
    // click or body can make links that run commands for whoever clicks them
    public String richText = "";
}
