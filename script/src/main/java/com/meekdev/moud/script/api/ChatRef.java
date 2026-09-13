package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import java.util.List;
import java.util.Map;

public interface ChatRef {

    long send(Map<String, Object> message);

    void edit(long id, Map<String, Object> changes);

    void delete(long id);

    void addPlayer(Instance channel, Instance body);

    void removePlayer(Instance channel, Instance body);

    void open(String prefill);

    void close();

    boolean isOpen();

    void clear();

    void setTarget(Instance channel);

    Instance target();

    List<Map<String, Object>> messages();

    void bubble(Instance target, String text, Map<String, Object> look);
}
