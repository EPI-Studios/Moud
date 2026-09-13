package com.meekdev.moud.core.instance;

// something that changes how the whole picture looks. it applies while it is in the tree and enabled,
// on every client, so a place can put one in a zone's folder and take it out again. of the ones the
// renderer keeps one of, the first enabled one in the tree is used
public class PostEffect extends Instance {

    public boolean enabled = true;
}
