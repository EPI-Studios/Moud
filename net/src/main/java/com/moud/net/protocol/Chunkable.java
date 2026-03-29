package com.moud.net.protocol;

import java.util.List;

/**
 * A message that can split itself into smaller pieces when its encoded size
 * would exceed the transport payload limit.
 *
 * <p>Implement this on any {@link Message} whose payload can grow unboundedly.
 * {@link com.moud.net.session.Session#send} detects this interface and
 * automatically sends each chunk in sequence instead of the original message.
 *
 * @param <T> the concrete message type produced by splitting
 */
public interface Chunkable<T extends Message> {
    /**
     * Split this message into chunks that each fit within {@code maxPayloadBytes}.
     * If the message already fits, return a single-element list containing {@code this}.
     */
    List<T> chunk(int maxPayloadBytes);
}
