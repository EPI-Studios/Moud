package com.moud.client.api.service;

import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private Context jsContext;
    public UIElement createElement(String type) {
        return new UIElement(type);
    }
    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("NetworkService received new GraalVM Context.");
    }

    public void cleanUp() {

        jsContext = null;
        LOGGER.info("InputService cleaned up.");
    }

    public static final class UIElement {
        private final String type;
        private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);

        private Context jsContext;
        private final Map<String, Object> properties = new ConcurrentHashMap<>();

        public UIElement(String type) {
            this.type = type;
        }

        public UIElement setProperty(String key, Object value) {
            properties.put(key, value);
            return this;
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public String getType() {
            return type;
        }

    }
}