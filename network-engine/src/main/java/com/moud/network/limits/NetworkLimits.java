package com.moud.network.limits;

public final class NetworkLimits {
    /**
     * Maximum allowed size (in bytes) for a decoded packet payload before attempting to deserialize it.
     * <p>
     * Override via {@code -Dmoud.network.maxPacketBytes=...}
     */
    public static final int MAX_PACKET_BYTES = Integer.getInteger("moud.network.maxPacketBytes", 4 * 1024 * 1024);

    /**
     * Maximum allowed size (in bytes) for the wrapper payload (channel + packet bytes).
     * <p>
     * Override via {@code -Dmoud.network.maxWrapperBytes=...}
     */
    public static final int MAX_WRAPPER_BYTES =
            Integer.getInteger("moud.network.maxWrapperBytes", MAX_PACKET_BYTES + 1024);

    /**
     * Maximum allowed size (in bytes) for channel identifiers inside wrapper payloads.
     * <p>
     * Override via {@code -Dmoud.network.maxChannelBytes=...}
     */
    public static final int MAX_CHANNEL_BYTES = Integer.getInteger("moud.network.maxChannelBytes", 256);

    /**
     * Maximum allowed byte length for strings during deserialization.
     * <p>
     * Override via {@code -Dmoud.network.maxStringBytes=...}
     */
    public static final int MAX_STRING_BYTES = Integer.getInteger("moud.network.maxStringBytes", MAX_PACKET_BYTES);

    /**
     * Maximum allowed byte length for byte arrays during deserialization.
     * <p>
     * Override via {@code -Dmoud.network.maxByteArrayBytes=...}
     */
    public static final int MAX_BYTE_ARRAY_BYTES = Integer.getInteger("moud.network.maxByteArrayBytes", MAX_PACKET_BYTES);

    /**
     * Maximum allowed element count for lists during deserialization.
     * <p>
     * Override via {@code -Dmoud.network.maxCollectionElements=...}
     */
    public static final int MAX_COLLECTION_ELEMENTS =
            Integer.getInteger("moud.network.maxCollectionElements", 100_000);

    /**
     * Maximum allowed entry count for maps during deserialization.
     * <p>
     * Override via {@code -Dmoud.network.maxMapEntries=...}
     */
    public static final int MAX_MAP_ENTRIES = Integer.getInteger("moud.network.maxMapEntries", 100_000);

    /**
     * Maximum allowed nesting depth for recursive map/list payloads.
     * <p>
     * Override via {@code -Dmoud.network.maxNestingDepth=...}
     */
    public static final int MAX_NESTING_DEPTH = Integer.getInteger("moud.network.maxNestingDepth", 64);

    private NetworkLimits() {
    }
}

