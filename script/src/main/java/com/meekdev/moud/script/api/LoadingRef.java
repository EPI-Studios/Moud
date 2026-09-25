package com.meekdev.moud.script.api;

public interface LoadingRef {

    String phase();

    double progress();

    String message();

    String server();

    boolean hold();

    void hold(boolean on);

    void finish();

    void retry();

    void quit();

    void openDownload();

    void listen(Listener listener);

    void forget(Listener listener);

    interface Listener {

        void changed();

        void ready();

        boolean failed(String reason, String kind);
    }
}
