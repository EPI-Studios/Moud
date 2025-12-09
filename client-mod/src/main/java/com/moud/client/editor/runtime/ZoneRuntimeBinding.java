package com.moud.client.editor.runtime;

import java.util.Map;

public record ZoneRuntimeBinding(String objectId, Map<String, Object> properties) {
    // todo: bind zone properties such as corner1, corner2
}
