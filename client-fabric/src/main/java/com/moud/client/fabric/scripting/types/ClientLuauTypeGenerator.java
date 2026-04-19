package com.moud.client.fabric.scripting.types;

import com.moud.core.scripts.luau.LuauTypeWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientLuauTypeGenerator {

    private static final String HEADER_RESOURCE = "scripting/moud-client-header.d.luau";

    private ClientLuauTypeGenerator() {}

    public static String build() {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb);
        sb.append('\n');
        sb.append(LuauTypeWriter.writeDeclarations(ClientLuauExportRegistry.classes()));
        return sb.toString();
    }

    public static void generate(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, build(), StandardCharsets.UTF_8);
    }

    private static void appendHeader(StringBuilder sb) {
        try (InputStream in = ClientLuauTypeGenerator.class.getClassLoader()
                .getResourceAsStream(HEADER_RESOURCE)) {
            if (in == null) {
                sb.append("--!strict\n-- (no client header bundled)\n");
                return;
            }
            sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + HEADER_RESOURCE, e);
        }
    }
}
