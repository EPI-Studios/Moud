package com.meekdev.moud.core.instance;

// a named cell of shared state that is not a fact about anything
//
// a score, a round timer, a game phase. these have nowhere sensible to live: they are not a property
// of a part, and hanging them off one means reading a part's colour to find out who is winning
//
// it is an instance so it costs nothing new: it has a name, a parent, a lifetime, it replicates
// because every property does, and `changed` already tells you when it moved. the value classes below
// carry one field each and no behaviour, which is the one place a class is allowed to be a field with
// a home -- what makes it a thing is that it is *shared*, and sharing is the instance's job
public class Value extends Instance {
}
