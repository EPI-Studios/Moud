package com.moud.client.physics;

import com.moud.api.physics.player.PlayerState;
import com.moud.client.util.PolyglotValueUtil;
import org.graalvm.polyglot.Value;

final class PlayerStateScriptCodec {
    private PlayerStateScriptCodec() {
    }

    static PlayerState read(Value value, PlayerState fallback) {
        PlayerState base = fallback != null ? fallback : PlayerState.at(0, 0, 0);
        if (value == null || value.isNull() || !value.hasMembers()) {
            return base;
        }

        double x = PolyglotValueUtil.readDouble(value, "x", base.x());
        double y = PolyglotValueUtil.readDouble(value, "y", base.y());
        double z = PolyglotValueUtil.readDouble(value, "z", base.z());

        float velX = PolyglotValueUtil.readFloat(value, "velX", base.velX());
        float velY = PolyglotValueUtil.readFloat(value, "velY", base.velY());
        float velZ = PolyglotValueUtil.readFloat(value, "velZ", base.velZ());

        boolean onGround = PolyglotValueUtil.readBoolean(value, "onGround", base.onGround());
        boolean collidingHorizontally = PolyglotValueUtil.readBoolean(
                value,
                "collidingHorizontally",
                base.collidingHorizontally()
        );

        return new PlayerState(x, y, z, velX, velY, velZ, onGround, collidingHorizontally);
    }
}

