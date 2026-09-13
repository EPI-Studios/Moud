package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import java.util.List;
import java.util.Map;

// the chat, from a place. messages are rich text (RichText) and cross as plain maps with the keys
// id, text, prefix, metadata, channel, source, body, timestamp and status
//
// one interface for both sides, because a place writes game.chat the same way on either. what only
// makes sense on one side throws on the other, with a message saying which side it belongs to
public interface ChatRef {

    // the server sends to a channel's members, or to one body with to; a client shows it in its own window.
    // keys: text, prefix, metadata, channel, from, to. answers the message id
    long send(Map<String, Object> message);

    // keys: text, prefix, metadata
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
}
