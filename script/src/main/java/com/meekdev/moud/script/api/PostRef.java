package com.meekdev.moud.script.api;

import java.util.List;

// how a delivery leaves this side
//
// the script layer only knows core, so what carries a message is handed in the way a camera and an
// input already are. the thing on the other end of this is the transport, and whether that is a queue
// in one process or a packet channel is not a question a place can ask
public interface PostRef {

    void toServer(int remote, List<Object> args, boolean reliable);

    void toClient(String player, int remote, List<Object> args, boolean reliable);

    void toAllClients(int remote, List<Object> args, boolean reliable);
}
