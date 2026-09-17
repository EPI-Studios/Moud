package com.meekdev.moud.script.api;

import java.util.List;

public interface InvokeRef {

    void toServer(int remote, int call, List<Object> args);

    void toClient(String player, int remote, int call, List<Object> args);
}
