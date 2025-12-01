package com.moud.example;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.Plugin;
import com.moud.plugin.api.command.CommandContext;
import com.moud.plugin.api.entity.GameObject;
import com.moud.plugin.api.entity.Light;
import com.moud.plugin.api.entity.Player;
import com.moud.plugin.api.events.PlayerJoinEvent;

import java.util.List;

public final class ExamplePlugin extends Plugin {
    private GameObject capsule;
    private Light capsuleLight;
    private boolean bloomEnabled;

    @Override
    protected void onLoad() throws Exception {
        super.onLoad();

        context().logger().info("Loading Example Plugin...");
    }

    @Override
    public void onEnable() {
        capsule = world().spawn("moud:models/capsule.obj")
                .at(0, 70, 0)
                .scale(1.25f)
                .physics(new Vector3(0.5f, 1.0f, 0.5f), 8f, Vector3.zero())
                .build();

        capsuleLight = world().light()
                .point()
                .at(0, 73, 0)
                .color(1.0f, 0.8f, 0.6f)
                .radius(25f)
                .brightness(2.0f)
                .create();

        schedule().every(10).seconds(() ->
                broadcast("example:heartbeat")
                        .with("ts", System.currentTimeMillis())
                        .send()
        );

        on(PlayerJoinEvent.class, event -> {
            Player joined = player(event.player());
            joined.sendMessage("Welcome " + joined.name() + "!")
                    .toast("Welcome", "Enjoy your stay!");
        });

        onClient("example:ping", event ->
                player(event.player())
                        .send("example:pong")
                        .with("echo", event.payload())
                        .dispatch());

        command("examplecapsule")
                .description("Teleport capsule to you")
                .executor(this::teleportCapsule)
                .register();

        command("examplebloom")
                .description("Toggle bloom post-processing")
                .executor(ctx -> toggleBloom())
                .register();

        command("exampleblueprints")
                .description("List saved scene blueprints")
                .executor(ctx -> listBlueprints(ctx.player()))
                .register();

        applyBloom();

        context().logger().info("Example Plugin enabled");
    }

    private void teleportCapsule(CommandContext ctx) {
        Player player = ctx.player();
        if (player == null) {
            return;
        }

        player.toast("Capsule teleported", "It now follows you.");
        capsule.teleport(player.position().add(new Vector3(0, 1, 0)));

        capsuleLight
                .moveTo(player.position().add(new Vector3(0, 3, 0)))
                .color(0.6f, 0.8f, 1.0f)
                .radius(18f)
                .brightness(2.5f)
                .update();
    }

    private void toggleBloom() {
        bloomEnabled = !bloomEnabled;
        if (bloomEnabled) {
            applyBloom();
        } else {
            context().rendering().removePostEffect("moud:bloom");
            context().players().broadcastActionBar("§7[Example] Bloom disabled");
        }
    }

    private void applyBloom() {
        context().rendering().applyPostEffect("moud:bloom");
        context().players().broadcastActionBar("§7[Example] Bloom enabled");
        bloomEnabled = true;
    }

    private void listBlueprints(Player player) {
        List<String> blueprints = context().scenes().listBlueprints();
        String message = blueprints.isEmpty() ? "No blueprints saved." : "Blueprints: " + String.join(", ", blueprints);
        if (player != null) {
            player.sendMessage(message);
        } else {
            logger().info(message);
        }
    }

    @Override
    public void onDisable() {
        if (capsule != null) {
            capsule.remove();
        }
        if (capsuleLight != null) {
            capsuleLight.remove();
        }
        context().rendering().clearPostEffects();

        context().logger().info("Example Plugin disabled");
    }
}
