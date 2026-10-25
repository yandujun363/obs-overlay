package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.component.AllDefaultOverlayComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat input bar (chat_bar).
 *
 * <p>{@code ChatScreen#extractRenderState} is everything the open chat draws: the input background,
 * the chat messages, every widget (that is where the {@code EditBox} lives) and the command
 * suggestions popup. Tagging the whole method as {@code chat_bar} and letting
 * {@link ChatComponentMixin} re-tag the messages gives exactly the upstream split: messages follow
 * the "chat" component, everything else follows "chat_bar".</p>
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void obs$chatBarStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.chatBar);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"))
    private void obs$chatBarEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }
}
