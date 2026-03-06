package com.moud.client.fabric.net;


import com.moud.net.session.Session;

public final class ClientSessionBus {
    private static volatile Session session;

    private ClientSessionBus() {
    }

    public static Session get() {
        return session;
    }

    public static void set(Session session) {
        ClientSessionBus.session = session;
    }
}

