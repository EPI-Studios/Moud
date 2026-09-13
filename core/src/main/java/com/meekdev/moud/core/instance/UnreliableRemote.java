package com.meekdev.moud.core.instance;

// a channel that trades ordering and delivery for not costing anything
//
// a separate class rather than a flag on Remote. "may arrive out
// of order, may not arrive at all" is a different contract, and a different contract should be a
// different type -- so a place cannot send a purchase down it by passing the wrong boolean
//
// what it is for: something that is continuously replaced and where the next one is along shortly. a
// cursor, a look direction, a held-key state. never a score, a payment, or anything counted
public final class UnreliableRemote extends Remote {
}
