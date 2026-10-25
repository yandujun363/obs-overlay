package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.component.AllDefaultOverlayComponents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags the chat messages themselves so they keep following the "chat" component even while the
 * chat screen is open (the surrounding {@code ChatScreen} extraction is tagged {@code chat_bar}).
 *
 * <p>Both call sites are covered: {@code Hud#extractChat} (HUD) and {@code ChatScreen}
 * (chat open).</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"))
    private void obs$chatStart(GuiGraphicsExtractor graphics, Font font, int a, int b, int c,
                               ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.chat);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN"))
    private void obs$chatEnd(GuiGraphicsExtractor graphics, Font font, int a, int b, int c,
                             ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }
}
