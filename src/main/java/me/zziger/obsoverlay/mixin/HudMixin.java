package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OBSOverlay;
import me.zziger.obsoverlay.OBSOverlayConfig;
import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.component.AllDefaultOverlayComponents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the HUD components while they are extracted into the {@code GuiRenderState}.
 *
 * <p>In 26.3 the HUD is no longer drawn directly; each component is "extracted" into the render
 * state. The pair of injections around every extract method is the 1:1 replacement for the old
 * {@code beginDraw}/{@code endDraw} framebuffer switching.</p>
 */
@Mixin(Hud.class)
public abstract class HudMixin {
    private static final String EXTRACT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;";

    /**
     * "Test icon": a small square that is always drawn into the overlay target. It must never show
     * up in the stream, which makes it a quick sanity check that the overlay is actually active.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$testIcon(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OBSOverlayConfig config = OBSOverlayConfig.get();
        if (config == null || !config.showTestIcon || !OBSOverlay.isActive()) {
            return;
        }
        OverlayComponentStack.pushTag("obs_overlay#test_icon");
        try {
            graphics.fill(0, 0, 16, 16, 0xFFFF00FF);
        } finally {
            OverlayComponentStack.pop();
        }
    }

    // ---- debug menu (F3) ----
    @Inject(method = "extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void obs$debugStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.debugMenu);
    }

    @Inject(method = "extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("RETURN"))
    private void obs$debugEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- chat ----
    @Inject(method = "extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$chatStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.chat);
    }

    @Inject(method = "extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$chatEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- tab list ----
    @Inject(method = "extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$tabListStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.playerList);
    }

    @Inject(method = "extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$tabListEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- scoreboard sidebar ----
    @Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$scoreboardStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.scoreboards);
    }

    @Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$scoreboardEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- action bar (/title actionbar) ----
    @Inject(method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$actionbarStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.actionbar);
    }

    @Inject(method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$actionbarEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- title / subtitle ----
    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$titleStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.titleSubtitle);
    }

    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$titleEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- status effects ----
    @Inject(method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$effectsStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.effects);
    }

    @Inject(method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$effectsEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- hotbar / health / hunger / experience ----
    @Inject(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void obs$mainHudStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.mainHud);
    }

    @Inject(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void obs$mainHudEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }

    // ---- subtitles (accessibility) ----
    @Inject(method = "extractSubtitleOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V", at = @At("HEAD"))
    private void obs$subtitlesStart(GuiGraphicsExtractor graphics, boolean bl, CallbackInfo ci) {
        OverlayComponentStack.push(AllDefaultOverlayComponents.subtitles);
    }

    @Inject(method = "extractSubtitleOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V", at = @At("RETURN"))
    private void obs$subtitlesEnd(GuiGraphicsExtractor graphics, boolean bl, CallbackInfo ci) {
        OverlayComponentStack.pop();
    }
}
