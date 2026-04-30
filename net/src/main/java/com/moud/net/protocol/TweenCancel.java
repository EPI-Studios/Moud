package com.moud.net.protocol;

import java.util.List;

public record TweenCancel(long nodeId, List<String> propertyKeys) implements Message {

    public boolean cancelsAll() {
        return propertyKeys == null || propertyKeys.isEmpty();
    }

    @Override
    public MessageType type() {
        return MessageType.TWEEN_CANCEL;
    }
}
