package com.meekdev.moud.script.api;

// the game's chat, from a place. messages are markup, see Markup
public interface ChatRef {

    // to one player by id, or to everyone when player is empty. on a client, only this client sees it
    void say(String markup, String player);
}
