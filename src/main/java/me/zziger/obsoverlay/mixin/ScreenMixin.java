package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OBSOverlayConfig;
import me.zziger.obsoverlay.OverlayComponentStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whole-screen overlays: everything a marked screen extracts ends up on the overlay target.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void obs$screenStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        OverlayComponentStack.pushTag(OBSOverlayConfig.isScreenOverlayed(screen) ? "obs_overlay#screen" : null);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void obs$screenEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    /**
     * Don't blur the world behind an overlaid screen: the blur would still be visible in the
     * capture and would give away that a menu is open. 1:1 port of the old {@code applyBlur} cancel.
     */
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void obs$skipBlur(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (OBSOverlayConfig.isScreenOverlayed((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
