package com.moud.server.minestom.engine;

import java.util.Objects;

public record JoinContext(
        String placeId,
        String reservedInstanceId,
        String reservationToken,
        byte[] payload
) {
    public JoinContext {
        Objects.requireNonNull(placeId, "placeId");
        if (placeId.isBlank()) {
            throw new IllegalArgumentException("placeId blank");
        }
    }

    public static JoinContext forPlace(String placeId) {
        return new JoinContext(placeId, null, null, null);
    }

    public static JoinContext forInstance(String placeId, String reservedInstanceId, String reservationToken) {
        return new JoinContext(placeId, reservedInstanceId, reservationToken, null);
    }

    public static JoinContext forInstanceWithPayload(String placeId, String reservedInstanceId, String reservationToken, byte[] payload) {
        return new JoinContext(placeId, reservedInstanceId, reservationToken, payload);
    }

    public boolean hasReservation() {
        return reservedInstanceId != null && !reservedInstanceId.isBlank();
    }
}
