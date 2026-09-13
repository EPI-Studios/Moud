package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;

// a stream of chat messages with its own members, like "general", "team" or "staff"
//
// a player is in it while a TextSource for them is inside it. what reaches whom is decided per
// message by shouldDeliver, which is where a place writes proximity chat, team chat or dead chat
public final class TextChannel extends Instance {

    // every player who joins is put in it
    public boolean autoJoin = true;

    // shown on the channel's tab and before its messages when a place does not style them
    public String displayName = "";
    public Color color = Color.WHITE;

    // the least time between two messages from the same player, in seconds
    @Prop(min = 0) public double slowMode;

    // the longest message a player may send, in characters of plain text
    @Prop(min = 1) public int maxLength = 256;

    // the server: (message, source) -> boolean. whether this message reaches the player of this source
    public final Callback shouldDeliver = new Callback();

    // the server: (message) -> boolean. whether a player's message is sent at all, before anything else
    public final Callback shouldSend = new Callback();

    // both sides: (message) -> table. the server may change text, prefix and metadata before it goes
    // out; a client may change how it looks in the window
    public final Callback onIncoming = new Callback();

    // a message arrived in this channel, on the side that heard it
    public final Signal<Object> messageReceived = new Signal<>();
}
