package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Rewind;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.script.api.HistoryRef;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerHistory implements HistoryRef {

    public static final ServerHistory INSTANCE = new ServerHistory();

    private static final int VIEW_DELAY_TICKS = 3;

    private final Rewind rewind = new Rewind();

    private ServerHistory() {}

    @Override
    public Rewind rewind() {
        return rewind;
    }

    @Override
    public double viewTime(String player) {
        MinecraftServer server = ServerScene.server();
        double now = rewind.now();
        if (server == null) return now;
        try {
            ServerPlayer who = server.getPlayerList().getPlayer(UUID.fromString(player));
            if (who == null) return now;
            return now - who.connection.latency() / 1000.0 - (double) VIEW_DELAY_TICKS / Rewind.TICKS_PER_SECOND;
        } catch (IllegalArgumentException notAPlayer) {
            return now;
        }
    }

    void note(InstanceTree tree, Change change) {
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Moved moved -> moved.id();
            case Change.Wrote wrote -> wrote.id();
            default -> -1;
        };
        if (id < 0) return;
        Instance instance = tree.byId(id);
        if (!(instance instanceof Spatial)) return;
        if (change instanceof Change.Wrote wrote) {
            String name = instance.def().properties()[wrote.property()].name();
            if (!name.equals("cframe") && !name.equals("size") && !name.equals("pivot")) return;
        }
        rewind.moved(instance);
    }

    void record(InstanceTree tree) {
        rewind.record(tree);
    }

    void clear() {
        rewind.clear();
    }
}
