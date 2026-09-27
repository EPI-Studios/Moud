package com.meekdev.moud.mod.client.editor.document;

import java.util.List;
import org.jspecify.annotations.Nullable;

record Relink(@Nullable InstanceRef holder, int holderAt, int property, @Nullable InstanceRef value, int valueAt) {

    @Nullable InstanceRef holder(List<InstanceRef> all) {
        return pick(holder, holderAt, all);
    }

    @Nullable InstanceRef value(List<InstanceRef> all) {
        return pick(value, valueAt, all);
    }

    private static @Nullable InstanceRef pick(@Nullable InstanceRef fixed, int at, List<InstanceRef> all) {
        if (fixed != null) return fixed;
        return at >= 0 && at < all.size() ? all.get(at) : null;
    }
}
