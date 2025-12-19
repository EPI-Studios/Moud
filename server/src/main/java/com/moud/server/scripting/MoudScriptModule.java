package com.moud.server.scripting;

import java.util.Objects;
import java.util.function.Supplier;

public interface MoudScriptModule {
    String getNamespace();

    Object getProxy();

    static MoudScriptModule of(String namespace, Supplier<Object> proxySupplier) {
        Objects.requireNonNull(proxySupplier, "proxySupplier");
        return new SupplierBacked(namespace, proxySupplier);
    }

    record SupplierBacked(String namespace, Supplier<Object> proxySupplier) implements MoudScriptModule {
        public SupplierBacked {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(proxySupplier, "proxySupplier");
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Object getProxy() {
            return proxySupplier.get();
        }
    }
}

