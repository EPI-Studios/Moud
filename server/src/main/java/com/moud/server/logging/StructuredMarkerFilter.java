package com.moud.server.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Custom marker filter to avoid dependency on logback-classic optional modules when using Gradle shadow.
 */
public class StructuredMarkerFilter extends Filter<ILoggingEvent> {
    private Marker marker;
    private FilterReply onMatch = FilterReply.NEUTRAL;
    private FilterReply onMismatch = FilterReply.NEUTRAL;

    public void setMarker(String markerName) {
        if (markerName != null) {
            this.marker = MarkerFactory.getMarker(markerName);
        }
    }

    public void setOnMatch(String reply) {
        this.onMatch = parseReply(reply, FilterReply.NEUTRAL);
    }

    public void setOnMismatch(String reply) {
        this.onMismatch = parseReply(reply, FilterReply.NEUTRAL);
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (marker == null) {
            return FilterReply.NEUTRAL;
        }
        Marker eventMarker = event.getMarker();
        if (eventMarker != null && eventMarker.contains(marker)) {
            return onMatch;
        }
        return onMismatch;
    }

    private FilterReply parseReply(String value, FilterReply defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return switch (value.toUpperCase()) {
            case "DENY" -> FilterReply.DENY;
            case "NEUTRAL" -> FilterReply.NEUTRAL;
            case "ACCEPT" -> FilterReply.ACCEPT;
            default -> defaultValue;
        };
    }
}

