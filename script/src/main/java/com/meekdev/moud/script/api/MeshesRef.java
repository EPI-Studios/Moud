package com.meekdev.moud.script.api;

import com.meekdev.moud.core.mesh.EditableMesh;
import java.util.concurrent.CompletableFuture;

public interface MeshesRef {

    CompletableFuture<EditableMesh> load(String source);
}
