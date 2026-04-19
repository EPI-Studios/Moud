package com.moud.client.fabric.scripting.types;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ClientLuauTypesMain {

    private static final String DEFAULT_OUTPUT = "core/src/main/resources/types/moud-client.d.luau";

    public static void main(String[] args) throws Exception {
        ClientLuauBindingsManifest.registerAll();
        Path out = args.length > 0 ? Paths.get(args[0]) : Paths.get(DEFAULT_OUTPUT).toAbsolutePath();
        ClientLuauTypeGenerator.generate(out);
        System.out.println("[luau-client-types] wrote " + out.toAbsolutePath().normalize());
    }
}
