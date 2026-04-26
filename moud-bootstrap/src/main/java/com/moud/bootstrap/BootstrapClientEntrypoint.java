package com.moud.bootstrap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.text.Text;

public final class BootstrapClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            BootstrapAgentInstaller.InstallationResult result = MoudBootstrap.agentInstallation();
            if (result == null || client.inGameHud == null) {
                return;
            }

            String message = switch (result.status()) {
                case INSTALLED -> "Moud updated your launcher profile. Restart once to activate.";
                case UNSUPPORTED_LAUNCHER -> "Moud could not auto-configure this launcher. Agent setup may require manual steps.";
                case FAILED -> "Moud agent setup failed: " + (result.message() == null ? "unknown error" : result.message());
                default -> null;
            };

            if (message != null && !message.isBlank()) {
                client.execute(() -> client.inGameHud.setOverlayMessage(Text.literal(message), false));
            }
        });
    }
}
