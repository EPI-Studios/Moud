package com.moud.client.mixin;

import com.moud.client.imgui.TextEditorCompat;
import imgui.extension.texteditor.TextEditorLanguageDefinition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(value = foundry.veil.api.client.imgui.VeilLanguageDefinitions.class, remap = false)
public abstract class VeilLanguageDefinitionsMixin {

    @Redirect(
        method = "createGlsl",
        at = @At(
            value = "INVOKE",
            target = "Limgui/extension/texteditor/TextEditorLanguageDefinition;setAutoIndentation(Z)V"
        ),
        require = 0
    )
    private static void moud$compatSetAutoIndentation(TextEditorLanguageDefinition definition, boolean enabled) {
        TextEditorCompat.setAutoIndentation(definition, enabled);
    }
}
