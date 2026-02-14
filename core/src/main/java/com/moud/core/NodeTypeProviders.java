package com.moud.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ServiceLoader;

public final class NodeTypeProviders {
    private NodeTypeProviders() {
    }

    public static void loadInto(NodeTypeRegistry registry) {
        ArrayList<NodeTypeProvider> providers = new ArrayList<>();
        for (NodeTypeProvider provider : ServiceLoader.load(NodeTypeProvider.class)) {
            providers.add(provider);
        }
        providers.sort(Comparator
                .comparingInt(NodeTypeProvider::order)
                .thenComparing(p -> p.getClass().getName()));
        for (NodeTypeProvider provider : providers) {
            provider.register(registry);
        }
    }
}
