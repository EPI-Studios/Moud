package com.moud.network.serializer;

import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.AnimationGson;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.serializer.PacketSerializer.TypeSerializer;


public final class AnimationClipSerializer implements TypeSerializer<AnimationClip> {
    @Override
    public void write(ByteBuffer buffer, AnimationClip value) {
        if (value == null) {
            buffer.writeString("");
            return;
        }
        buffer.writeString(AnimationGson.instance().toJson(value));
    }

    @Override
    public AnimationClip read(ByteBuffer buffer) {
        String json = buffer.readString();
        if (json == null || json.isEmpty()) {
            return null;
        }
        return AnimationGson.instance().fromJson(json, AnimationClip.class);
    }
}
