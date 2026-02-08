package com.moud.server.network.handler;

/**
 * Interface for grouping related packet handlers together.
 */
public interface PacketHandlerGroup {
    /**
     * Register all packet handlers in this group with the registry.
     *
     * @param registry the packet registry to register handlers with
     */
    void register(PacketRegistry registry);
}
