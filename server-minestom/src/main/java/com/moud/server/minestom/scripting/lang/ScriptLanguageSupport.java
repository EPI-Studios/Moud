package com.moud.server.minestom.scripting.lang;


import com.moud.server.minestom.scripting.ScriptLanguage;

public record ScriptLanguageSupport(
        ScriptLanguage language,
        boolean available,
        String unavailableReason
) {
    public String messageForPath(String scriptPath) {
        String path = scriptPath == null ? "" : scriptPath.trim();
        String reason = unavailableReason == null ? "" : unavailableReason.trim();
        if (reason.isEmpty()) {
            return language.displayName() + " script is not available";
        }
        if (path.isEmpty()) {
            return reason;
        }
        return reason + " (" + path + ")";
    }
}
