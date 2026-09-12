package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;
import java.util.List;

// a channel across the client and the server boundary
//
// it is an instance, which is the best idea in roblox's version of this and the reason to copy it
// rather than invent: a channel has a name, a parent, a lifetime and an owner, "does this channel
// exist" is the same question as "does this part exist", and destroying whatever it hangs off closes
// it. a registry keyed by string has none of that and leaks every channel a place ever opened
//
// the two directions are deliberately not symmetric, because the two sides are not:
//
//   the client says   fireServer(...)        and the server hears onServer(player, ...)
//   the server says   fireClient(player, ...) or fireAllClients(...)
//                                            and the client hears onClient(...)
//
// the player on the server side is *supplied here* and is not an argument the client passes. that is
// the whole security property of the design: a client cannot claim to be somebody else, because it
// never says who it is. everything else it sends is a claim and has to be treated as one
public class Remote extends Instance {

    // what a client sent, on the server
    public final Signal<Sent> onServer = new Signal<>();

    // what the server sent, on a client
    public final Signal<Sent> onClient = new Signal<>();

    // one delivery. `from` is the player it came from on the server side and empty on the client,
    // where there is only one possible sender and naming it would be noise
    public record Sent(String from, List<Object> args) {}
}
