package com.meekdev.moud.script.api;

import java.util.List;

public interface PostRef {

    String me();

    void toServer(int remote, List<Object> args, boolean reliable);

    void toClient(String player, int remote, List<Object> args, boolean reliable);

    void toAllClients(int remote, List<Object> args, boolean reliable);
}
