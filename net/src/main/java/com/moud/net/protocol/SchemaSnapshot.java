package com.moud.net.protocol;

import com.moud.core.NodeTypeDef;
import java.util.List;

public record SchemaSnapshot(long schemaRevision, List<NodeTypeDef> types) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCHEMA_SNAPSHOT;
    }
}
