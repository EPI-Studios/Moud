package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.api.LoadingRef;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

public final class LoadingApi implements LoadingRef {

    public static final LoadingApi PLACE = new LoadingApi(false);
    public static final LoadingApi SCRIPT = new LoadingApi(true);

    private final boolean lasting;

    private LoadingApi(boolean lasting) {
        this.lasting = lasting;
    }

    @Override
    public String phase() {
        return LoadingState.phase();
    }

    @Override
    public double progress() {
        return LoadingState.progress();
    }

    @Override
    public String message() {
        return LoadingState.message();
    }

    @Override
    public String server() {
        return LoadingState.server();
    }

    @Override
    public boolean hold() {
        return LoadingState.hold();
    }

    @Override
    public void hold(boolean on) {
        LoadingState.hold(on);
    }

    @Override
    public void finish() {
        LoadingPlace.finish();
    }

    @Override
    public void retry() {
        if (!LoadingState.failing()) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (!LoadingState.failing()) return;
            if (ServerConnect.active()) ServerConnect.start(client);
            else Launch.reopen();
        });
    }

    @Override
    public void quit() {
        GameState.INSTANCE.quit();
    }

    @Override
    public void openDownload() {
        String link = ServerConnect.download();
        if (link.isEmpty()) return;
        MoudMod.LOG.info("opening {}", link);
        Util.getPlatform().openUri(link);
    }

    @Override
    public void listen(Listener listener) {
        LoadingState.listen(listener, lasting);
    }

    @Override
    public void forget(Listener listener) {
        LoadingState.forget(listener);
    }
}
