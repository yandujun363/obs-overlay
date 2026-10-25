package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.component.AllDefaultOverlayComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Subtitles (accessibility).
 *
 * <p>Tagging {@code Hud#extractSubtitleOverlay} would be wrong in 26.3: that method has a deferred
 * mode, and the HUD always uses it. With {@code deferred = true} it only stores a {@code Runnable}
 * and draws nothing; the actual {@code SubtitleOverlay#extractRenderState} call happens later, from
 * {@code Gui#extractRenderState} → {@code Hud#extractDeferredSubtitles} (so subtitles end up on top
 * of an open screen). The tag would already have been popped at that point, and the subtitles would
 * leak into the capture.</p>
 *
 * <p>{@code SubtitleOverlay#extractRenderState} is the single drawing entry for both paths (it is
 * only ever referenced from {@code Hud}), so it is tagged here instead.</p>
 */
@Mixin(SubtitleOverlay.class)
public abstract class SubtitleOverlayMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void obs$subtitlesStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.subtitles);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("RETURN"))
    private void obs$subtitlesEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }
}
