package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.LinkedHashMap;

public final class InstanceAttributes {

    private InstanceAttributes() {}

    public static void install(Host host) {
        host.api().declare(HostSignal.decl("AttributeSignal", "(name: string) -> ()"));
        Members shared = host.instances().shared();
        shared.method("setAttribute", "(name: string, value: any) -> ()", a -> {
            Instance instance = a.self();
            if (host.instances().edited(instance)) {
                host.edits().attribute(instance, a.string(1), a.get(2));
                return null;
            }
            host.instances().checkAttribute(instance);
            try {
                Instances.setAttribute(instance, a.string(1), a.get(2));
            } catch (IllegalArgumentException e) {
                throw new HostError("%s", e.getMessage());
            }
            return null;
        });
        shared.method("getAttribute", "(name: string) -> any", a -> a.self().attribute(a.string(1)));
        shared.method("getAttributes", "() -> { [string]: any }", a -> new LinkedHashMap<>(a.self().attributes()));
        shared.method("getAttributeChangedSignal", "(name: string) -> AnySignal",
                a -> host.instances().attributeSignal(a.self(), a.string(1)));
        shared.method("getPropertyChangedSignal", "(property: string) -> AnySignal", a -> {
            Instance instance = a.self();
            String name = a.string(1);
            HostSignal signal = host.instances().propertySignal(instance, name);
            if (signal == null) throw new HostError("%s has no property '%s'", instance.def().name(), name);
            return signal;
        });
    }
}
