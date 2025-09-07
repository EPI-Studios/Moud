package com.moud.server.entity;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptedEntity extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptedEntity.class);
    private static final int MAX_TICK_ERRORS = 5;

    private final Value jsInstance;
    private Value onTickFunction;
    private int consecutiveTickErrors = 0;
    private boolean tickDisabled = false;

    public ScriptedEntity(EntityType entityType, Value jsInstance) {
        super(entityType);
        this.jsInstance = jsInstance;

        if (jsInstance != null && jsInstance.hasMember("onTick")) {
            Value onTick = jsInstance.getMember("onTick");
            if (onTick.canExecute()) {
                this.onTickFunction = onTick;
            } else {
                LOGGER.warn("Entity script object has an 'onTick' member, but it is not a function.");
            }
        }
    }

    @Override
    public void tick(long time) {
        super.tick(time);
        if (onTickFunction != null && !tickDisabled) {
            try {
                onTickFunction.execute(jsInstance);
                consecutiveTickErrors = 0;
            } catch (Exception e) {
                consecutiveTickErrors++;
                LOGGER.error("An error occurred while executing onTick for a scripted entity (Error {}/{}).", consecutiveTickErrors, MAX_TICK_ERRORS, e);
                if (consecutiveTickErrors >= MAX_TICK_ERRORS) {
                    tickDisabled = true;
                    onTickFunction = null;
                    LOGGER.error("Disabling onTick for entity {} due to repeated errors. The script will no longer be ticked.", getUuid());
                }
            }
        }
    }
}