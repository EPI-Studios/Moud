package com.meekdev.moud.script.api;

import java.util.List;

public interface RosterRef {

    List<PlayerRef> all();

    PlayerRef find(String id);
}
