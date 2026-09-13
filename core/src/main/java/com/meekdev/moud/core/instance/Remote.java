package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;
import java.util.List;

public class Remote extends Instance {

    public String accepts = "";

    public final Signal<Sent> onServer = new Signal<>();

    public final Signal<Sent> onClient = new Signal<>();

    public record Sent(String from, List<Object> args) {}
}
