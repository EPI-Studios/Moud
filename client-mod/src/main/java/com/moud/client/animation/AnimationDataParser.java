
package com.moud.client.animation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AnimationDataParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationDataParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AnimationData parseAnimation(String jsonContent) {
        try {
            return MAPPER.readValue(jsonContent, AnimationData.class);
        } catch (IOException e) {
            LOGGER.error("Failed to parse animation JSON", e);
            return null;
        }
    }
}