package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Rewind;

// the server's record of where things were, and how far behind each player sees
public interface HistoryRef {

    Rewind rewind();

    // the server time the player wearing this body was looking at, which is now less their round trip
    // and the delay their copy is drawn at. now when the body is nobody's
    double viewTime(String player);
}
