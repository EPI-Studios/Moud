package com.moud.server.scripting;


public interface ScriptPlayerContextProvider {
    boolean hasItem(String itemId);

    float getHealth();

    boolean hasEffect(String effectId);

    Object getData(String key);

    String getBlock(double x, double y, double z);

    boolean isInZone(double x, double y, double z, String zoneId);
}

