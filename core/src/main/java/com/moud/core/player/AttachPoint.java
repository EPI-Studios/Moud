package com.moud.core.player;

/** Valid named attachment points on a player skeleton. */
public enum AttachPoint {
    root,
    center,
    head,
    above_head,
    right_hand,
    left_hand,
    right_item,
    left_item,
    right_foot,
    left_foot;

    /** The string id stored in node properties. */
    public String id() { return name(); }
}
