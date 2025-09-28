package com.moud.client.api.service;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientScript.Console");
    private static final int MAX_LOG_LENGTH = 4096;
    private Context jsContext;

    @HostAccess.Export
    public void log(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.info(message);
    }

    @HostAccess.Export
    public void warn(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.warn(message);
    }

    @HostAccess.Export
    public void error(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.error(message);
    }

    @HostAccess.Export
    public void debug(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.debug(message);
    }

    private String formatArgs(Object[] args) {
        if (args.length == 0) return "";
        if (args.length == 1) return formatSingleArg(args[0]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(formatSingleArg(args[i]));
        }
        return sb.toString();
    }

    private String formatSingleArg(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof String) return (String) arg;
        if (arg instanceof Number) return arg.toString();
        if (arg instanceof Boolean) return arg.toString();

        try {
            return arg.toString();
        } catch (Exception e) {
            return "[Object: " + arg.getClass().getSimpleName() + "]";
        }
    }

    public void cleanUp() {
        jsContext = null;
        LOGGER.info("ConsoleAPI cleaned up.");
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("ConsoleAPI received new GraalVM Context.");
    }
}