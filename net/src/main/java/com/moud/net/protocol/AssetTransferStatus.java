package com.moud.net.protocol;

public enum AssetTransferStatus {
    OK(0),
    REJECTED(1),
    ALREADY_PRESENT(2),
    NOT_FOUND(3),
    ERROR(4);

    private final int id;

    AssetTransferStatus(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static AssetTransferStatus fromId(int id) {
        for (AssetTransferStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        return ERROR;
    }
}

