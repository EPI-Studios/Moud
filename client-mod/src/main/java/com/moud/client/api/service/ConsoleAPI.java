package com.moud.client.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientScript.Console");

    public void log(Object... args) {
        LOGGER.info(formatArgs(args));
    }

    public void warn(Object... args) {
        LOGGER.warn(formatArgs(args));
    }

    public void error(Object... args) {
        LOGGER.error(formatArgs(args));
    }

    private String formatArgs(Object[] args) {
        if (args.length == 0) return "";
        if (args.length == 1) return String.valueOf(args[0]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}