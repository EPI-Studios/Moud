package com.moud.server.minestom.scripting.typescript;

import com.moud.core.NodeTypeRegistry;
import org.graalvm.polyglot.Engine;

import java.util.Map;

public final class TypeScriptContext {

    private final TypeScriptTranspiler transpiler;
    private final TypeScriptPipeline pipeline;
    private final NodeTypeSchema schema;
    private final MoudRuntimeShim shim;

    public TypeScriptContext(NodeTypeRegistry registry, Engine graalEngine) {
        this.transpiler = new TypeScriptTranspiler(graalEngine);
        this.shim = new MoudRuntimeShim(registry);
        this.pipeline = new TypeScriptPipeline(transpiler, shim);
        this.schema = new NodeTypeSchema(registry);
    }

    public TypeScriptPipeline pipeline() { return pipeline; }

    public Map<String, Map<String, String>> typeSchema() { return schema.schema(); }

    public String shimSource() { return shim.source(); }

    public void close() {
        transpiler.close();
    }
}
