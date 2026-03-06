# Miry dans un projet Fabric (tuto universel)

Ce README explique comment intégrer **Miry** (lib UI OpenGL) dans *n’importe quel* mod **Fabric** (client), avec un exemple complet réutilisable (pas lié à un projet spécifique).

## Prérequis

- Java `21` (côté dev) + un projet Fabric basé sur Gradle/Loom.
- Miry disponible soit via un dépôt Maven, soit via un checkout local (composite build Gradle).

## 1) Ajouter Miry côté Gradle

### Option A — Dépendance Maven (simple)

Dans le `build.gradle` du module Fabric (souvent `:client` ou `:client-fabric`), ajoute un repo qui contient Miry puis la dépendance.

Exemple (générique) :

```gradle
repositories {
    mavenCentral()
    maven { url = "https://maven.fabricmc.net/" }

    // Ex: Miry publié sur un repo Maven (ici: repo.minestom.com)
    maven {
        url = "https://repo.minestom.com/"
        content {
            includeGroup "com.miry"
        }
    }
}

dependencies {
    // Jar-in-jar: embed Miry dans ton mod (recommandé si Miry n'est pas un mod Fabric)
    include(implementation("com.miry:miry:1.0-SNAPSHOT") {
        // Important: Minecraft embarque déjà LWJGL -> évite les doublons
        exclude group: "org.lwjgl"
    })
}
```

Notes :
- `include(implementation(...))` = Miry est embarqué dans ton JAR (via Loom “jar-in-jar”).
- L’exclusion `org.lwjgl` évite les conflits de classes natives/LWJGL (très fréquent en environnement Minecraft).

#### Copie/colle (template)

Fichier complet : `client-fabric/build.gradle`

```gradle
plugins {
    id 'fabric-loom' version '1.11-SNAPSHOT'
}

repositories {
    mavenCentral()
    maven { url = 'https://maven.fabricmc.net/' }

    maven {
        // Repo Maven où Miry est publié (remplace si besoin)
        url = 'https://repo.minestom.com/'
        content {
            includeGroup 'com.miry'
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.1"
    mappings "net.fabricmc:yarn:1.21.1+build.1:v2"
    modImplementation "net.fabricmc:fabric-loader:0.17.2"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1"

    include(implementation("com.miry:miry:1.0-SNAPSHOT") {
        // Minecraft embarque déjà LWJGL -> évite les doublons/conflits
        exclude group: "org.lwjgl"
    })
}

loom {
    mixin {
        defaultRefmapName = "mymod.refmap.json"
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### Option B — Checkout local (composite build Gradle)

Pratique si tu développes Miry en même temps que ton mod, sans publier d’artefact.

Dans `settings.gradle` :

```gradle
// Chemin vers ton checkout local Miry (adapte)
def miryDir = file("../miry")
if (miryDir.exists()) {
    includeBuild(miryDir)
    println("Using local Miry checkout at ${miryDir}")
}
```

Ensuite, tu gardes la dépendance normale côté module Fabric :

```gradle
dependencies {
    include(implementation("com.miry:miry:1.0-SNAPSHOT") {
        exclude group: "org.lwjgl"
    })
}
```

Gradle fera une substitution automatique vers le build local si le projet Miry expose bien l’artefact `com.miry:miry`.

## 2) Exemple complet (non-spécifique) : rendu + input + mixins

L’objectif : un overlay Miry toggleable (`F8`), rendu en HUD quand aucune `Screen` vanilla n’est ouverte, et avec forwarding clavier/texte via mixins.

### Fichier : `client-fabric/src/main/resources/fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "mymod",
  "version": "1.0.0",
  "name": "MyMod",
  "environment": "client",
  "entrypoints": {
    "client": ["com.example.mymod.client.MyModClient"]
  },
  "mixins": ["mymod.client.mixins.json"],
  "depends": {
    "minecraft": "1.21.1",
    "fabricloader": ">=0.17.2",
    "fabric-api": "*"
  }
}
```

### Fichier : `client-fabric/src/main/resources/mymod.client.mixins.json`

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.mymod.client.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "KeyboardMixin",
    "MouseMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### Fichier : `client-fabric/src/main/java/com/example/mymod/client/MyModClient.java`

```java
package com.example.mymod.client;

import com.example.mymod.client.ui.MyMiryOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public final class MyModClient implements ClientModInitializer {
    public static MyModClient INSTANCE;

    private KeyBinding toggleUiKey;
    private boolean uiOpen;
    private MyMiryOverlay overlay;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        toggleUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mymod.toggle_ui",
                GLFW.GLFW_KEY_F8,
                "category.mymod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> clientTick());

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) return;
            if (!uiOpen) return;
            if (client.currentScreen != null) return;

            if (overlay == null) {
                overlay = new MyMiryOverlay();
                overlay.setOpen(true);
            }
            overlay.render();
        });
    }

    private void clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        while (toggleUiKey != null && toggleUiKey.wasPressed()) {
            uiOpen = !uiOpen;
            if (overlay != null) overlay.setOpen(uiOpen);

            if (uiOpen) {
                client.mouse.unlockCursor();
            } else if (client.currentScreen == null) {
                client.mouse.lockCursor();
            }
        }
    }

    public MyMiryOverlay overlay() {
        return overlay;
    }
}
```

### Fichier : `client-fabric/src/main/java/com/example/mymod/client/ui/MyMiryOverlay.java`

```java
package com.example.mymod.client.ui;

import com.miry.graphics.batch.BatchRenderer;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.font.FontAtlas;
import com.miry.ui.font.FontData;
import com.miry.ui.font.TextRenderer;
import com.miry.ui.input.UiInput;
import com.miry.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public final class MyMiryOverlay {
    private final Theme theme = new Theme();
    private final Ui ui = new Ui(theme);
    private final UiInput input = new UiInput();

    private UiContext uiContext;
    private BatchRenderer batch;
    private FontAtlas fontAtlas;
    private boolean open;
    private boolean prevLeft;
    private float pendingScrollY;

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public void render() {
        if (!open) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        if (client.currentScreen != null) return;

        Window window = client.getWindow();
        int w = window.getScaledWidth();
        int h = window.getScaledHeight();
        if (w <= 0 || h <= 0) return;

        long handle = window.getHandle();
        ensureInitialized(window, handle);
        if (batch == null || uiContext == null) return;

        float framebufferScale = window.getFramebufferWidth() / (float) Math.max(1, w);
        framebufferScale = Math.max(0.1f, framebufferScale);

        float mx = (float) (client.mouse.getX() * w / (double) Math.max(1, window.getWidth()));
        float my = (float) (client.mouse.getY() * h / (double) Math.max(1, window.getHeight()));

        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean leftPressed = left && !prevLeft;
        boolean leftReleased = !left && prevLeft;
        prevLeft = left;

        float scrollY = pendingScrollY;
        pendingScrollY = 0.0f;
        input.setMousePos(mx, my).setMouseButtons(left, leftPressed, leftReleased).setScrollY(scrollY);
        ui.beginFrame(input, 1.0f / 60.0f);
        uiContext.update(1.0f / 60.0f);

        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthWasEnabled) GL11.glDisable(GL11.GL_DEPTH_TEST);

        batch.begin(w, h, framebufferScale);
        batch.drawRect(10, 10, 240, 90, 0xCC111111);
        batch.drawText("Hello Miry (Fabric)", 22, 44, 0xFFFFFFFF);
        batch.drawText("F8 = toggle UI", 22, 66, 0xFFB0B0B0);
        batch.end();

        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void ensureInitialized(Window window, long handle) {
        if (batch != null) return;
        if (GLFW.glfwGetCurrentContext() == 0L) return;

        batch = new BatchRenderer(50_000);

        int ww = Math.max(1, window.getWidth());
        float scale = window.getFramebufferWidth() / (float) ww;
        scale = Math.max(0.1f, scale);
        int atlasSize = Math.min(2048, Math.max(1024, Math.round(768.0f * scale)));
        fontAtlas = new FontAtlas(FontData.loadDefault(), 16.0f, atlasSize, scale, FontAtlas.Mode.COVERAGE);
        batch.setTextRenderer(new TextRenderer(fontAtlas));

        uiContext = new UiContext(handle, UiContext.Config.MANUAL_INPUT);
    }

    public void pushKeyEvent(int key, int scancode, int glfwAction, int mods) {
        if (!open || uiContext == null) return;
        KeyEvent.Action act = switch (glfwAction) {
            case GLFW.GLFW_PRESS -> KeyEvent.Action.PRESS;
            case GLFW.GLFW_RELEASE -> KeyEvent.Action.RELEASE;
            case GLFW.GLFW_REPEAT -> KeyEvent.Action.REPEAT;
            default -> null;
        };
        if (act == null) return;
        uiContext.keyboard().pushKeyEvent(key, scancode, act, mods);
    }

    public void pushCharEvent(int codepoint) {
        if (!open || uiContext == null) return;
        uiContext.keyboard().pushCharEvent(codepoint);
    }

    public void pushScroll(double dy) {
        if (!open) return;
        pendingScrollY += (float) dy;
    }
}
```

## 3) Forward des inputs clavier/souris vers Miry (indispensable)

Minecraft capture déjà les inputs. Pour que Miry reçoive clavier + texte + scroll, le pattern le plus robuste est de **mixin** les handlers vanilla et de forward vers `UiContext`.

### Clavier (keys + text input)

Fichier : `client-fabric/src/main/java/com/example/mymod/client/mixin/KeyboardMixin.java`
- `Keyboard.onKey(...)` → `overlay.pushKeyEvent(key, scancode, action, modifiers)`
- `Keyboard.onChar(...)` → `overlay.pushCharEvent(codePoint)`

#### Copie/colle (template)

```java
package com.example.mymod.client.mixin;

import com.example.mymod.client.MyModClient;
import com.example.mymod.client.ui.MyMiryOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public final class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void mymod$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) return;

        MyModClient entry = MyModClient.INSTANCE;
        if (entry == null) return;
        MyMiryOverlay overlay = entry.overlay();
        if (overlay == null || !overlay.isOpen()) return;

        if (key == GLFW.GLFW_KEY_F8) return; // laisse passer le toggle
        overlay.pushKeyEvent(key, scancode, action, modifiers);
        ci.cancel(); // bloque vanilla
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void mymod$onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) return;

        MyModClient entry = MyModClient.INSTANCE;
        if (entry == null) return;
        MyMiryOverlay overlay = entry.overlay();
        if (overlay == null || !overlay.isOpen()) return;

        overlay.pushCharEvent(codePoint);
        ci.cancel(); // bloque vanilla
    }
}
```

Et dans l’overlay, pousse dans Miry :
- `uiContext.keyboard().pushKeyEvent(...)`
- `uiContext.keyboard().pushCharEvent(...)`

### Souris (scroll, boutons, lock)

Fichier : `client-fabric/src/main/java/com/example/mymod/client/mixin/MouseMixin.java`
- `Mouse.onMouseScroll(...)` → accumuler `scrollY` (puis le consommer dans le render)
- optionnel : bloquer `updateMouse()` quand l’UI est active pour éviter que vanilla consomme les deltas

#### Copie/colle (template)

```java
package com.example.mymod.client.mixin;

import com.example.mymod.client.MyModClient;
import com.example.mymod.client.ui.MyMiryOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void mymod$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client == null || client.currentScreen != null) return;

        MyModClient entry = MyModClient.INSTANCE;
        if (entry == null) return;
        MyMiryOverlay overlay = entry.overlay();
        if (overlay == null || !overlay.isOpen()) return;

        overlay.pushScroll(vertical);
        ci.cancel();
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void mymod$updateMouse(CallbackInfo ci) {
        if (client == null || client.currentScreen != null) return;

        MyModClient entry = MyModClient.INSTANCE;
        if (entry == null) return;
        MyMiryOverlay overlay = entry.overlay();
        if (overlay == null || !overlay.isOpen()) return;

        // Optionnel : empêche vanilla de consommer les deltas quand l’UI est ouverte.
        ci.cancel();
    }
}
```

L’important : quand l’UI est ouverte, **annule** l’input vanilla (`ci.cancel()`) pour éviter double-consommation.

### Déclarer les mixins

Dans ton `*.mixins.json` (ex: `mymod.client.mixins.json`), ajoute tes mixins `KeyboardMixin` / `MouseMixin`.

Le fichier `client-fabric/src/main/resources/mymod.client.mixins.json` est déjà donné dans l’exemple complet plus haut.

## 4) Pièges fréquents

- **LWJGL en double** : toujours exclure `org.lwjgl` côté Miry quand tu es dans Minecraft.
- **Contexte OpenGL** : n’initialise textures/framebuffers Miry que si un contexte est courant (`GLFW.glfwGetCurrentContext() != 0`).
- **Coordonnées souris** : utilise les coordonnées *scaled* (`getScaledWidth/Height`) sinon ton hit-test sera faux avec l’UI scale.
- **Screens vanilla** : ne rends pas ton overlay par-dessus une `Screen` (pause menu, chat, etc.) sauf si tu le veux vraiment.

## Build / Run (générique)

Dans le module Fabric :

```bash
./gradlew :client-fabric:runClient
```
