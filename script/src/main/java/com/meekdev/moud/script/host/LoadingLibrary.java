package com.meekdev.moud.script.host;

import com.meekdev.moud.script.api.LoadingRef;

final class LoadingLibrary {

    private LoadingLibrary() {}

    static void install(Host host, Members game) {
        LoadingRef ref = host.loading();
        if (ref == null) return;
        host.api().alias("LoadingPhase", "\"lookup\" | \"connecting\" | \"joining\" | \"world\" | \"ready\"");
        host.api().alias("LoadingFailure", "\"connect\" | \"update\" | \"closed\" | \"disconnected\"");
        host.api().declare(HostSignal.decl("LoadingFailedSignal", "(reason: string, kind: LoadingFailure) -> ()"));
        HostSignal changed = new HostSignal(host, "AnySignal", "game.loading.changed");
        HostSignal ready = new HostSignal(host, "AnySignal", "game.loading.ready");
        HostSignal failed = new HostSignal(host, "LoadingFailedSignal", "game.loading.failed");
        LoadingRef.Listener listener = new LoadingRef.Listener() {
            @Override
            public void changed() {
                changed.fire();
            }

            @Override
            public void ready() {
                ready.fire();
            }

            @Override
            public boolean failed(String reason, String kind) {
                if (failed.count() == 0) return false;
                failed.fire(reason, kind);
                return true;
            }
        };
        ref.listen(listener);
        host.onClose(() -> {
            ref.forget(listener);
            changed.clear();
            ready.clear();
            failed.clear();
        });
        Members loading = new Members("Loading")
                .field("phase", "LoadingPhase", ref::phase)
                .field("progress", "number", ref::progress)
                .field("message", "string", ref::message)
                .field("server", "string", ref::server)
                .field("hold", "boolean", ref::hold, value -> {
                    if (!(value instanceof Boolean on)) throw new HostError("game.loading.hold expects true or false");
                    ref.hold(on);
                })
                .value("changed", "AnySignal", changed)
                .value("ready", "AnySignal", ready)
                .value("failed", "LoadingFailedSignal", failed)
                .method("finish", "() -> ()", a -> {
                    ref.finish();
                    return null;
                })
                .method("retry", "() -> ()", a -> {
                    ref.retry();
                    return null;
                })
                .method("quit", "() -> ()", a -> {
                    ref.quit();
                    return null;
                })
                .method("openDownload", "() -> ()", a -> {
                    ref.openDownload();
                    return null;
                });
        game.value("loading", "Loading", loading);
        host.declare(loading);
    }
}
