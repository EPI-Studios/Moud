package com.moud.client.fabric.assets;

import com.moud.core.assets.AssetHash;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AssetsClient {
    public static final int CHUNK_BYTES = 256 * 1024;
    private static final int MAX_CHUNKS_PER_TICK = 2;

    public interface Listener {
        default void onManifest(AssetManifestResponse response) {
        }

        default void onUploadAck(AssetUploadAck ack) {
        }

        default void onDownloadComplete(AssetHash hash, AssetTransferStatus status, byte[] bytes, String message) {
        }
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextRequestId = 1;

    private final Queue<UploadTask> uploadQueue = new ArrayDeque<>();
    private UploadTask activeUpload;

    private final ConcurrentHashMap<AssetHash, DownloadTask> downloads = new ConcurrentHashMap<>();

    public void setListener(Listener listener) {
        Objects.requireNonNull(listener);
        listeners.clear();
        listeners.add(listener);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void requestManifest(Session session) {
        if (session == null) {
            return;
        }
        session.send(Lane.ASSETS, new AssetManifestRequest(nextRequestId++));
    }

    public void upload(Session session, ResPath path, byte[] bytes, AssetType type) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(bytes, "bytes");
        if (session == null) {
            return;
        }
        AssetHash hash = AssetHash.sha256(bytes);
        AssetType t = type == null ? AssetType.BINARY : type;
        uploadQueue.add(new UploadTask(path, hash, bytes, t));
        ensureUploadStarted(session);
    }

    public void download(Session session, AssetHash hash) {
        Objects.requireNonNull(hash, "hash");
        if (session == null) {
            return;
        }
        downloads.put(hash, new DownloadTask(hash));
        session.send(Lane.ASSETS, new AssetDownloadRequest(hash));
    }

    public void tick(Session session) {
        if (session == null) {
            return;
        }
        ensureUploadStarted(session);
        UploadTask task = activeUpload;
        if (task == null || task.state != UploadState.SENDING_CHUNKS) {
            return;
        }
        int sent = 0;
        while (sent < MAX_CHUNKS_PER_TICK && task.offset < task.bytes.length) {
            int len = Math.min(CHUNK_BYTES, task.bytes.length - task.offset);
            byte[] chunk = Arrays.copyOfRange(task.bytes, task.offset, task.offset + len);
            session.send(Lane.ASSETS, new AssetUploadChunk(task.hash, task.nextIndex++, chunk));
            task.offset += len;
            sent++;
        }
        if (task.offset >= task.bytes.length) {
            session.send(Lane.ASSETS, new AssetUploadComplete(task.path, task.hash));
            task.state = UploadState.AWAITING_COMPLETE_ACK;
        }
    }

    public void onMessage(Message message) {
        if (message instanceof AssetManifestResponse response) {
            for (Listener listener : listeners) {
                listener.onManifest(response);
            }
        } else if (message instanceof AssetUploadAck ack) {
            onUploadAck(ack);
            for (Listener listener : listeners) {
                listener.onUploadAck(ack);
            }
        } else if (message instanceof AssetDownloadBegin begin) {
            onDownloadBegin(begin);
        } else if (message instanceof AssetDownloadChunk chunk) {
            onDownloadChunk(chunk);
        } else if (message instanceof AssetDownloadComplete complete) {
            onDownloadComplete(complete);
        }
    }

    private void ensureUploadStarted(Session session) {
        if (activeUpload != null) {
            return;
        }
        UploadTask next = uploadQueue.poll();
        if (next == null) {
            return;
        }
        activeUpload = next;
        next.state = UploadState.AWAITING_BEGIN_ACK;
        session.send(Lane.ASSETS, new AssetUploadBegin(next.path, next.hash, next.bytes.length, next.assetType));
    }

    private void onUploadAck(AssetUploadAck ack) {
        UploadTask task = activeUpload;
        if (task == null) {
            return;
        }
        if (!task.hash.equals(ack.hash()) || !task.path.equals(ack.path())) {
            return;
        }
        if (ack.status() == AssetTransferStatus.OK && task.state == UploadState.AWAITING_BEGIN_ACK) {
            task.state = UploadState.SENDING_CHUNKS;
            return;
        }
        if (ack.status() == AssetTransferStatus.ALREADY_PRESENT && task.state == UploadState.AWAITING_BEGIN_ACK) {
            activeUpload = null;
            return;
        }
        if (task.state == UploadState.AWAITING_COMPLETE_ACK) {
            activeUpload = null;
        }
        if (ack.status() != AssetTransferStatus.OK) {
            activeUpload = null;
        }
    }

    private void onDownloadBegin(AssetDownloadBegin begin) {
        if (begin.hash() == null) {
            return;
        }
        DownloadTask task = downloads.get(begin.hash());
        if (task == null) {
            return;
        }
        task.status = begin.status();
        task.message = begin.message();
        task.type = begin.assetType();
        task.expectedSize = begin.sizeBytes();
        if (begin.status() != AssetTransferStatus.OK) {
            // server will likely follow with complete; keep task until then.
            return;
        }
        task.buffer = new ByteArrayOutputStream((int) Math.min(begin.sizeBytes(), 1024 * 1024));
    }

    private void onDownloadChunk(AssetDownloadChunk chunk) {
        DownloadTask task = downloads.get(chunk.hash());
        if (task == null || task.buffer == null) {
            return;
        }
        task.buffer.writeBytes(chunk.bytes());
    }

    private void onDownloadComplete(AssetDownloadComplete complete) {
        AssetHash hash = complete.hash();
        if (hash == null) {
            return;
        }
        DownloadTask task = downloads.remove(hash);
        if (task == null) {
            return;
        }
        AssetTransferStatus status = complete.status() == null ? AssetTransferStatus.ERROR : complete.status();
        byte[] bytes = task.buffer == null ? new byte[0] : task.buffer.toByteArray();
        for (Listener listener : listeners) {
            listener.onDownloadComplete(hash, status, bytes, complete.message());
        }
    }

    private enum UploadState {
        AWAITING_BEGIN_ACK,
        SENDING_CHUNKS,
        AWAITING_COMPLETE_ACK
    }

    private static final class UploadTask {
        private final ResPath path;
        private final AssetHash hash;
        private final byte[] bytes;
        private final AssetType assetType;

        private UploadState state;
        private int nextIndex;
        private int offset;

        private UploadTask(ResPath path, AssetHash hash, byte[] bytes, AssetType assetType) {
            this.path = path;
            this.hash = hash;
            this.bytes = bytes;
            this.assetType = assetType;
        }
    }

    private static final class DownloadTask {
        private final AssetHash hash;
        private long expectedSize;
        private AssetType type = AssetType.BINARY;
        private AssetTransferStatus status = AssetTransferStatus.ERROR;
        private String message = "";
        private ByteArrayOutputStream buffer;

        private DownloadTask(AssetHash hash) {
            this.hash = hash;
        }
    }
}
