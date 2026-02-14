package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;

import java.util.Arrays;
import java.util.Objects;

public record AssetDownloadChunk(AssetHash hash, int index, byte[] bytes) implements Message {
    public AssetDownloadChunk {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(bytes, "bytes");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
    }

    @Override
    public MessageType type() {
        return MessageType.ASSET_DOWNLOAD_CHUNK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssetDownloadChunk other)) {
            return false;
        }
        return index == other.index && hash.equals(other.hash) && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, index, Arrays.hashCode(bytes));
    }
}
