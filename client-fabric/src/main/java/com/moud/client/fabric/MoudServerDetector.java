package com.moud.client.fabric;

import com.moud.client.fabric.util.ClientDebugLog;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MoudServerDetector {
    public static final String MOTD_MARKER = "[Moud]";

    private static final Set<String> moudAddresses = ConcurrentHashMap.newKeySet();
    private static volatile ServerInfo lastConnectInfo;
    private static volatile String lastConnectRawAddress;

    public static void rememberConnectAttempt(String rawAddress, ServerInfo info) {
        lastConnectInfo = info;
        lastConnectRawAddress = rawAddress;
    }

    public static ServerInfo lastConnectInfo() { return lastConnectInfo; }
    public static String lastConnectRawAddress() { return lastConnectRawAddress; }

    private MoudServerDetector() {}

    public static void markAddress(String rawAddress) {
        String key = normalize(rawAddress);
        if (key != null) moudAddresses.add(key);
    }

    public static boolean isMoud(String rawAddress) {
        String key = normalize(rawAddress);
        return key != null && moudAddresses.contains(key);
    }

    public static boolean isMoud(ServerInfo info) {
        return info != null && isMoud(info.address);
    }

    public static void scanAndMark(ServerInfo info) {
        if (info == null) return;
        boolean labelHit = containsMarker(info.label);
        boolean countHit = containsMarker(info.playerCountLabel);
        boolean versionHit = containsMarker(info.version);
        if (labelHit || countHit || versionHit) {
            markAddress(info.address);
            ClientDebugLog.info("MoudDetect",
                    "marked " + info.address + " labelHit=" + labelHit
                            + " countHit=" + countHit + " versionHit=" + versionHit);
        }
    }

    private static boolean containsMarker(Text text) {
        if (text == null) return false;
        return text.getString().contains(MOTD_MARKER);
    }

    private static String normalize(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || !ServerAddress.isValid(rawAddress)) {
            return null;
        }
        ServerAddress address = ServerAddress.parse(rawAddress.trim());
        String host = address.getAddress();
        if (host == null || host.isBlank()) return null;
        return host.trim().toLowerCase(Locale.ROOT) + ":" + address.getPort();
    }
}
