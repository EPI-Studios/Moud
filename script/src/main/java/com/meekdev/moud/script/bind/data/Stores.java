package com.meekdev.moud.script.bind.data;

import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.bind.LuaJson;
import com.meekdev.moud.script.bind.LuaTables;
import java.util.List;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Stores {

    private Stores() {}

    public static void install(LuaState state, StoreRef store) {
        LuaTables.global(state, "__moud_store_get", s -> {
            LuaJson.push(s, store.get(s.checkString(1), s.checkString(2)));
            return 1;
        });
        LuaTables.global(state, "__moud_store_set", s -> {
            store.set(s.checkString(1), s.checkString(2), s.isNoneOrNil(3) ? null : LuaJson.read(s, 3));
            return 0;
        });
        LuaTables.global(state, "__moud_store_update", s -> {
            if (s.type(3) != LuaType.FUNCTION) throw s.error("update expects a function");
            String result = store.update(s.checkString(1), s.checkString(2), old -> {
                s.pushValue(3);
                LuaJson.push(s, old);
                s.call(1, 1);
                String next = s.isNoneOrNil(-1) ? null : LuaJson.read(s, -1);
                s.pop(1);
                return next;
            });
            LuaJson.push(s, result);
            return 1;
        });
        LuaTables.global(state, "__moud_store_keys", s -> {
            List<String> keys = store.keys(s.checkString(1), s.checkString(2), (int) s.checkNumber(3));
            s.createTable(keys.size(), 0);
            for (int n = 0; n < keys.size(); n++) {
                s.pushString(keys.get(n));
                s.rawSetI(-2, n + 1);
            }
            return 1;
        });
        LuaTables.global(state, "__moud_store_lease", s -> {
            s.pushBoolean(store.lease(s.checkString(1), s.checkString(2), s.checkString(3), s.checkNumber(4)));
            return 1;
        });
        LuaTables.global(state, "__moud_store_release", s -> {
            store.release(s.checkString(1), s.checkString(2), s.checkString(3));
            return 0;
        });
        LuaTables.global(state, "__moud_store_owner", s -> {
            s.pushString(store.owner());
            return 1;
        });
    }

}
