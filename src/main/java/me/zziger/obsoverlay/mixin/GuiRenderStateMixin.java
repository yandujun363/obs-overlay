package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OverlayComponentStack;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags every render-state entry added while an overlay component is being extracted.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {
    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void obs$tagElement(GuiElementRenderState elementState, CallbackInfo ci) {
        OverlayComponentStack.tag(elementState);
    }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"))
    private void obs$tagBlit(BlitRenderState blitState, CallbackInfo ci) {
        OverlayComponentStack.tag(blitState);
    }

    @Inject(method = "addGlyphToCurrentLayer", at = @At("HEAD"))
    private void obs$tagGlyph(GuiElementRenderState glyphState, CallbackInfo ci) {
        OverlayComponentStack.tag(glyphState);
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void obs$tagText(GuiTextRenderState textState, CallbackInfo ci) {
        OverlayComponentStack.tag(textState);
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void obs$tagItem(GuiItemRenderState itemState, CallbackInfo ci) {
        OverlayComponentStack.tag(itemState);
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void obs$tagPictureInPicture(PictureInPictureRenderState state, CallbackInfo ci) {
        OverlayComponentStack.tag(state);
    }
}
