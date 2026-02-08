package com.moud.network.serializer;

import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;

import java.util.Map;

public final class UIElementDefinitionSerializer implements PacketSerializer.TypeSerializer<MoudPackets.UIElementDefinition> {
    @Override
    public void write(ByteBuffer buffer, MoudPackets.UIElementDefinition value) {
        buffer.writeString(value.id());
        buffer.writeString(value.type());
        String parentId = value.parentId();
        buffer.writeBoolean(parentId != null);
        if (parentId != null) {
            buffer.writeString(parentId);
        }
        MapSerializerUtil.writeStringObjectMap(buffer, value.props());
    }

    @Override
    public MoudPackets.UIElementDefinition read(ByteBuffer buffer) {
        String id = buffer.readString();
        String type = buffer.readString();
        String parentId = buffer.readBoolean() ? buffer.readString() : null;
        Map<String, Object> props = MapSerializerUtil.readStringObjectMap(buffer);
        return new MoudPackets.UIElementDefinition(id, type, parentId, props);
    }
}
