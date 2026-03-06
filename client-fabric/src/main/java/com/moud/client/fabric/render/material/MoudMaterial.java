package com.moud.client.fabric.render.material;

import java.util.Map;
import java.util.Objects;

public record MoudMaterial(
        String shader,
        Map<String, Param> params
) {
    public MoudMaterial {
        Objects.requireNonNull(shader, "shader");
        if (params == null) {
            params = Map.of();
        } else {
            params = Map.copyOf(params);
        }
    }

    public sealed interface Param permits Param.Number, Param.Bool, Param.Vec, Param.Texture, Param.StringParam {
        record Number(float value) implements Param {
        }

        record Bool(boolean value) implements Param {
        }

        record Vec(float[] values) implements Param {
            public Vec {
                Objects.requireNonNull(values, "values");
            }
        }

        record Texture(String textureRef) implements Param {
            public Texture {
                Objects.requireNonNull(textureRef, "textureRef");
            }
        }

        record StringParam(String value) implements Param {
            public StringParam {
                Objects.requireNonNull(value, "value");
            }
        }
    }
}

