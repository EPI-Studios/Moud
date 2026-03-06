package com.moud.net.wire.codec;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetDownloadBegin;
import com.moud.net.protocol.AssetDownloadChunk;
import com.moud.net.protocol.AssetDownloadComplete;
import com.moud.net.protocol.AssetDownloadRequest;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.AssetUploadBegin;
import com.moud.net.protocol.AssetUploadChunk;
import com.moud.net.protocol.AssetUploadComplete;
import com.moud.net.wire.WireIo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AssetCodec {
    private AssetCodec() {
    }

    public static void writeAssetManifestResponse(ByteBuffer out, AssetManifestResponse response) {
        WireIo.writeLong(out, response.requestId());
        List<AssetManifestResponse.Entry> entries = response.entries() == null ? List.of() : response.entries();
        WireIo.writeVarInt(out, entries.size());
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                WireIo.writeString(out, "");
                WireIo.writeString(out, "");
                WireIo.writeLong(out, 0L);
                writeAssetType(out, AssetType.BINARY);
                continue;
            }
            AssetMeta meta = entry.meta();
            WireIo.writeString(out, entry.path().value());
            WireIo.writeString(out, meta.hash().hex());
            WireIo.writeLong(out, meta.sizeBytes());
            writeAssetType(out, meta.type());
        }
    }

    public static AssetManifestResponse readAssetManifestResponse(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid asset manifest count: " + count);
        }
        List<AssetManifestResponse.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String path = WireIo.readString(in);
            String hash = WireIo.readString(in);
            long size = WireIo.readLong(in);
            AssetType type = readAssetType(in);
            if (path == null || path.isBlank() || hash == null || hash.isBlank()) {
                continue;
            }
            entries.add(new AssetManifestResponse.Entry(
                    new ResPath(path),
                    new AssetMeta(new AssetHash(hash), size, type)
            ));
        }
        return new AssetManifestResponse(requestId, List.copyOf(entries));
    }

    public static void writeAssetUploadBegin(ByteBuffer out, AssetUploadBegin begin) {
        WireIo.writeString(out, begin.path() == null ? "" : begin.path().value());
        WireIo.writeString(out, begin.hash() == null ? "" : begin.hash().hex());
        WireIo.writeLong(out, begin.sizeBytes());
        writeAssetType(out, begin.assetType());
    }

    public static AssetUploadBegin readAssetUploadBegin(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        long size = WireIo.readLong(in);
        AssetType type = readAssetType(in);
        return new AssetUploadBegin(path, hash, size, type);
    }

    public static void writeAssetUploadAck(ByteBuffer out, AssetUploadAck ack) {
        WireIo.writeString(out, ack.path() == null ? "" : ack.path().value());
        WireIo.writeString(out, ack.hash() == null ? "" : ack.hash().hex());
        writeStatus(out, ack.status());
        WireIo.writeString(out, ack.message());
    }

    public static AssetUploadAck readAssetUploadAck(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetUploadAck(path, hash, status, message);
    }

    public static void writeAssetUploadChunk(ByteBuffer out, AssetUploadChunk chunk) {
        WireIo.writeString(out, chunk.hash().hex());
        WireIo.writeVarInt(out, chunk.index());
        writeBytes(out, chunk.bytes());
    }

    public static AssetUploadChunk readAssetUploadChunk(ByteBuffer in) {
        AssetHash hash = new AssetHash(WireIo.readString(in));
        int index = WireIo.readVarInt(in);
        byte[] bytes = readBytes(in);
        return new AssetUploadChunk(hash, index, bytes);
    }

    public static void writeAssetUploadComplete(ByteBuffer out, AssetUploadComplete complete) {
        WireIo.writeString(out, complete.path() == null ? "" : complete.path().value());
        WireIo.writeString(out, complete.hash() == null ? "" : complete.hash().hex());
    }

    public static AssetUploadComplete readAssetUploadComplete(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        return new AssetUploadComplete(path, hash);
    }

    public static void writeAssetDownloadRequest(ByteBuffer out, AssetDownloadRequest request) {
        WireIo.writeString(out, request.hash() == null ? "" : request.hash().hex());
    }

    public static AssetDownloadRequest readAssetDownloadRequest(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        return new AssetDownloadRequest(hash);
    }

    public static void writeAssetDownloadBegin(ByteBuffer out, AssetDownloadBegin begin) {
        WireIo.writeString(out, begin.hash() == null ? "" : begin.hash().hex());
        WireIo.writeLong(out, begin.sizeBytes());
        writeAssetType(out, begin.assetType());
        writeStatus(out, begin.status());
        WireIo.writeString(out, begin.message());
    }

    public static AssetDownloadBegin readAssetDownloadBegin(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        long size = WireIo.readLong(in);
        AssetType type = readAssetType(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetDownloadBegin(hash, size, type, status, message);
    }

    public static void writeAssetDownloadChunk(ByteBuffer out, AssetDownloadChunk chunk) {
        WireIo.writeString(out, chunk.hash().hex());
        WireIo.writeVarInt(out, chunk.index());
        writeBytes(out, chunk.bytes());
    }

    public static AssetDownloadChunk readAssetDownloadChunk(ByteBuffer in) {
        AssetHash hash = new AssetHash(WireIo.readString(in));
        int index = WireIo.readVarInt(in);
        byte[] bytes = readBytes(in);
        return new AssetDownloadChunk(hash, index, bytes);
    }

    public static void writeAssetDownloadComplete(ByteBuffer out, AssetDownloadComplete complete) {
        WireIo.writeString(out, complete.hash() == null ? "" : complete.hash().hex());
        writeStatus(out, complete.status());
        WireIo.writeString(out, complete.message());
    }

    public static AssetDownloadComplete readAssetDownloadComplete(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetDownloadComplete(hash, status, message);
    }

    // --- Size estimations ---

    public static int assetManifestResponseSize(AssetManifestResponse response) {
        int size = WireIo.longSize(response.requestId());
        List<AssetManifestResponse.Entry> entries = response.entries() == null ? List.of() : response.entries();
        size += WireIo.varIntSize(entries.size());
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                size += WireIo.stringSize("") + WireIo.stringSize("") + WireIo.longSize(0L) + WireIo.varIntSize(0);
                continue;
            }
            size += WireIo.stringSize(entry.path().value());
            size += WireIo.stringSize(entry.meta().hash().hex());
            size += WireIo.longSize(entry.meta().sizeBytes());
            size += WireIo.varIntSize(entry.meta().type() == null ? 0 : entry.meta().type().ordinal());
        }
        return size;
    }

    public static int assetUploadBeginSize(AssetUploadBegin begin) {
        return WireIo.stringSize(begin.path() == null ? "" : begin.path().value())
                + WireIo.stringSize(begin.hash() == null ? "" : begin.hash().hex())
                + WireIo.longSize(begin.sizeBytes())
                + WireIo.varIntSize(begin.assetType() == null ? 0 : begin.assetType().ordinal());
    }

    public static int assetUploadAckSize(AssetUploadAck ack) {
        return WireIo.stringSize(ack.path() == null ? "" : ack.path().value())
                + WireIo.stringSize(ack.hash() == null ? "" : ack.hash().hex())
                + WireIo.varIntSize(ack.status() == null ? AssetTransferStatus.ERROR.id() : ack.status().id())
                + WireIo.stringSize(ack.message());
    }

    public static int assetUploadChunkSize(AssetUploadChunk chunk) {
        byte[] bytes = chunk.bytes();
        int len = bytes == null ? 0 : bytes.length;
        return WireIo.stringSize(chunk.hash() == null ? "" : chunk.hash().hex())
                + WireIo.varIntSize(chunk.index())
                + WireIo.varIntSize(len) + len;
    }

    public static int assetUploadCompleteSize(AssetUploadComplete complete) {
        return WireIo.stringSize(complete.path() == null ? "" : complete.path().value())
                + WireIo.stringSize(complete.hash() == null ? "" : complete.hash().hex());
    }

    public static int assetDownloadRequestSize(AssetDownloadRequest request) {
        return WireIo.stringSize(request.hash() == null ? "" : request.hash().hex());
    }

    public static int assetDownloadBeginSize(AssetDownloadBegin begin) {
        return WireIo.stringSize(begin.hash() == null ? "" : begin.hash().hex())
                + WireIo.longSize(begin.sizeBytes())
                + WireIo.varIntSize(begin.assetType() == null ? 0 : begin.assetType().ordinal())
                + WireIo.varIntSize(begin.status() == null ? AssetTransferStatus.ERROR.id() : begin.status().id())
                + WireIo.stringSize(begin.message());
    }

    public static int assetDownloadChunkSize(AssetDownloadChunk chunk) {
        byte[] bytes = chunk.bytes();
        int len = bytes == null ? 0 : bytes.length;
        return WireIo.stringSize(chunk.hash() == null ? "" : chunk.hash().hex())
                + WireIo.varIntSize(chunk.index())
                + WireIo.varIntSize(len) + len;
    }

    public static int assetDownloadCompleteSize(AssetDownloadComplete complete) {
        return WireIo.stringSize(complete.hash() == null ? "" : complete.hash().hex())
                + WireIo.varIntSize(complete.status() == null ? AssetTransferStatus.ERROR.id() : complete.status().id())
                + WireIo.stringSize(complete.message());
    }

    private static void writeBytes(ByteBuffer out, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        WireIo.writeVarInt(out, bytes.length);
        out.put(bytes);
    }

    private static byte[] readBytes(ByteBuffer in) {
        int len = WireIo.readVarInt(in);
        if (len < 0 || len > 1_048_576) {
            throw new IllegalArgumentException("Invalid bytes length: " + len);
        }
        if (in.remaining() < len) {
            throw new IllegalArgumentException("Bytes truncated");
        }
        byte[] bytes = new byte[len];
        in.get(bytes);
        return bytes;
    }

    private static void writeAssetType(ByteBuffer out, AssetType type) {
        WireIo.writeVarInt(out, type == null ? 0 : type.ordinal());
    }

    private static AssetType readAssetType(ByteBuffer in) {
        int ordinal = WireIo.readVarInt(in);
        AssetType[] values = AssetType.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return AssetType.BINARY;
        }
        return values[ordinal];
    }

    private static void writeStatus(ByteBuffer out, AssetTransferStatus status) {
        WireIo.writeVarInt(out, status == null ? AssetTransferStatus.ERROR.id() : status.id());
    }

    private static AssetTransferStatus readStatus(ByteBuffer in) {
        return AssetTransferStatus.fromId(WireIo.readVarInt(in));
    }

    private static ResPath readResPathOrNull(ByteBuffer in) {
        String value = WireIo.readString(in);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new ResPath(value);
    }

    private static AssetHash readHashOrNull(ByteBuffer in) {
        String value = WireIo.readString(in);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new AssetHash(value);
    }
}
