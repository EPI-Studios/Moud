package com.moud.net.protocol;

import java.util.List;

public interface Chunkable<T extends Message> {
    List<T> chunk(int maxPayloadBytes);
}
