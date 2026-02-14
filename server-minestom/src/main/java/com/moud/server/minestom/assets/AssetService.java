package com.moud.server.minestom.assets;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetManifest;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.AssetDownloadBegin;
import com.moud.net.protocol.AssetDownloadChunk;
import com.moud.net.protocol.AssetDownloadComplete;
import com.moud.net.protocol.AssetDownloadRequest;
import com.moud.net.protocol.AssetManifestRequest;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.AssetUploadBegin;
import com.moud.net.protocol.AssetUploadChunk;
import com.moud.net.protocol.AssetUploadComplete;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetService {
    private static final int CHUNK_BYTES_MAX = 512 * 1024;
    private static final long MAX_ASSET_BYTES = 128L * 1024L * 1024L;

    private final AssetStore store;
    private final boolean uploadsEnabled;
    private final Map<UUID, UploadContext> uploads = new ConcurrentHashMap<>();

    public AssetService(AssetStore store) {
        this(store, true);
    }

    public AssetService(AssetStore store, boolean uploadsEnabled) {
        this.store = Objects.requireNonNull(store);
        this.uploadsEnabled = uploadsEnabled;
    }

    public AssetStore store() {
        return store;
    }

    public void onMessage(UUID playerId, Session session, Message message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(message, "message");

        if (message instanceof AssetManifestRequest request) {
            handleManifestRequest(session, request);
        } else if (message instanceof AssetUploadBegin begin) {
            handleUploadBegin(playerId, session, begin);
        } else if (message instanceof AssetUploadChunk chunk) {
            handleUploadChunk(playerId, session, chunk);
        } else if (message instanceof AssetUploadComplete complete) {
            handleUploadComplete(playerId, session, complete);
        } else if (message instanceof AssetDownloadRequest request) {
            handleDownloadRequest(session, request);
        }
    }

    private void handleManifestRequest(Session session, AssetManifestRequest request) {
        AssetManifest manifest = store.manifest();
        ArrayList<AssetManifestResponse.Entry> entries = new ArrayList<>(manifest.entries().size());
        for (Map.Entry<ResPath, AssetMeta> entry : manifest.entries().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            entries.add(new AssetManifestResponse.Entry(entry.getKey(), entry.getValue()));
        }
        session.send(Lane.ASSETS, new AssetManifestResponse(request.requestId(), java.util.List.copyOf(entries)));
    }

    private void handleUploadBegin(UUID playerId, Session session, AssetUploadBegin begin) {
        if (!uploadsEnabled) {
            session.send(Lane.ASSETS, new AssetUploadAck(begin.path(), begin.hash(), AssetTransferStatus.REJECTED, "uploads disabled"));
            return;
        }
        ResPath path = begin.path();
        AssetHash hash = begin.hash();
        if (path == null || hash == null) {
            session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.REJECTED, "missing path/hash"));
            return;
        }
        if (begin.sizeBytes() < 0 || begin.sizeBytes() > MAX_ASSET_BYTES) {
            session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.REJECTED, "invalid size"));
            return;
        }
        if (uploads.containsKey(playerId)) {
            session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.REJECTED, "upload in progress"));
            return;
        }

        AssetMeta meta = new AssetMeta(hash, begin.sizeBytes(), begin.assetType() == null ? AssetType.BINARY : begin.assetType());
        try {
            if (store.hasBlob(hash)) {
                store.putMapping(path, meta);
                session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.ALREADY_PRESENT, "blob exists"));
                return;
            }
        } catch (Exception e) {
            session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.ERROR, e.getMessage()));
            return;
        }

        uploads.put(playerId, new UploadContext(path, meta));
        session.send(Lane.ASSETS, new AssetUploadAck(path, hash, AssetTransferStatus.OK, "send chunks"));
    }

    private void handleUploadChunk(UUID playerId, Session session, AssetUploadChunk chunk) {
        UploadContext ctx = uploads.get(playerId);
        if (ctx == null) {
            return;
        }
        if (!ctx.meta.hash().equals(chunk.hash())) {
            uploads.remove(playerId);
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "hash mismatch"));
            return;
        }
        if (chunk.index() != ctx.nextIndex) {
            uploads.remove(playerId);
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "unexpected chunk index"));
            return;
        }
        byte[] bytes = chunk.bytes();
        if (bytes.length > CHUNK_BYTES_MAX) {
            uploads.remove(playerId);
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "chunk too large"));
            return;
        }
        if (ctx.received + bytes.length > ctx.meta.sizeBytes()) {
            uploads.remove(playerId);
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "size overflow"));
            return;
        }

        ctx.buffer.writeBytes(bytes);
        ctx.received += bytes.length;
        ctx.nextIndex++;
    }

    private void handleUploadComplete(UUID playerId, Session session, AssetUploadComplete complete) {
        UploadContext ctx = uploads.remove(playerId);
        if (ctx == null) {
            return;
        }
        if (!ctx.path.equals(complete.path()) || !ctx.meta.hash().equals(complete.hash())) {
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "complete mismatch"));
            return;
        }
        byte[] bytes = ctx.buffer.toByteArray();
        if (bytes.length != ctx.meta.sizeBytes()) {
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "size mismatch"));
            return;
        }
        AssetHash computed = AssetHash.sha256(bytes);
        if (!computed.equals(ctx.meta.hash())) {
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.REJECTED, "sha256 mismatch"));
            return;
        }
        try {
            store.put(ctx.path, ctx.meta, bytes);
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.OK, "stored"));
        } catch (IOException e) {
            session.send(Lane.ASSETS, new AssetUploadAck(ctx.path, ctx.meta.hash(), AssetTransferStatus.ERROR, e.getMessage()));
        }
    }

    private void handleDownloadRequest(Session session, AssetDownloadRequest request) {
        AssetHash hash = request.hash();
        if (hash == null) {
            session.send(Lane.ASSETS, new AssetDownloadBegin(null, 0L, AssetType.BINARY, AssetTransferStatus.REJECTED, "missing hash"));
            session.send(Lane.ASSETS, new AssetDownloadComplete(null, AssetTransferStatus.REJECTED, "missing hash"));
            return;
        }
        if (!store.hasBlob(hash)) {
            session.send(Lane.ASSETS, new AssetDownloadBegin(hash, 0L, AssetType.BINARY, AssetTransferStatus.NOT_FOUND, "not found"));
            session.send(Lane.ASSETS, new AssetDownloadComplete(hash, AssetTransferStatus.NOT_FOUND, "not found"));
            return;
        }

        AssetMeta meta = store.metaByHash(hash);
        AssetType type = meta == null ? AssetType.BINARY : meta.type();
        byte[] bytes;
        try {
            bytes = store.readBlob(hash);
        } catch (IOException e) {
            session.send(Lane.ASSETS, new AssetDownloadBegin(hash, 0L, AssetType.BINARY, AssetTransferStatus.ERROR, e.getMessage()));
            session.send(Lane.ASSETS, new AssetDownloadComplete(hash, AssetTransferStatus.ERROR, e.getMessage()));
            return;
        }

        session.send(Lane.ASSETS, new AssetDownloadBegin(hash, bytes.length, type, AssetTransferStatus.OK, ""));
        int index = 0;
        for (int off = 0; off < bytes.length; off += CHUNK_BYTES_MAX) {
            int len = Math.min(CHUNK_BYTES_MAX, bytes.length - off);
            byte[] chunk = java.util.Arrays.copyOfRange(bytes, off, off + len);
            session.send(Lane.ASSETS, new AssetDownloadChunk(hash, index++, chunk));
        }
        session.send(Lane.ASSETS, new AssetDownloadComplete(hash, AssetTransferStatus.OK, ""));
    }

    private static final class UploadContext {
        private final ResPath path;
        private final AssetMeta meta;
        private final ByteArrayOutputStream buffer;
        private int nextIndex;
        private long received;

        private UploadContext(ResPath path, AssetMeta meta) {
            this.path = path;
            this.meta = meta;
            this.buffer = new ByteArrayOutputStream((int) Math.min(meta.sizeBytes(), 1024 * 1024));
        }
    }
}
