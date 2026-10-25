package me.zziger.obsoverlay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import me.zziger.obsoverlay.OBSOverlay;
import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.OverlayRenderer;
import me.zziger.obsoverlay.OverlayTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Splits the GUI draw list into "normal" and "overlay" batches.
 *
 * <p>{@code GuiRenderer} builds a sorted list of {@code Draw} batches and already knows how to draw
 * an index range into an arbitrary {@link RenderTarget} (that is how the blur pass works). We reuse
 * that: batches whose elements were tagged during extraction are executed against the overlay
 * target instead of the main render target.</p>
 *
 * <p>Batches are forced to break at component boundaries (see {@link #obs$beforeElement}) so a
 * single batch can never contain both hidden and visible content — otherwise the whole batch would
 * either leak into the capture or disappear from the screen.</p>
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    @Final
    private List<?> draws;

    @Shadow
    private int firstDrawIndexAfterBlur;

    @Shadow
    @Final
    private Projection guiProjection;

    @Shadow
    @Final
    private ProjectionMatrixBuffer guiProjectionMatrixBuffer;

    @Shadow
    private StagedVertexBuffer.Draw previousDraw;

    /** Tag per entry of {@code draws}, index aligned. */
    @Unique
    private final List<Object> obs$drawTags = new ArrayList<>();

    @Unique
    private Object obs$lastTag;

    @Invoker("executeDrawRange")
    abstract void obs$executeDrawRange(Supplier<String> label, RenderTarget target, GpuBufferSlice dynamicTransforms,
                                       int startIndex, int endIndex);

    @Inject(method = "prepare", at = @At("HEAD"))
    private void obs$resetTags(CallbackInfo ci) {
        obs$drawTags.clear();
        obs$lastTag = null;
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void obs$beforeElement(GuiElementRenderState elementState, CallbackInfo ci) {
        Object tag = OverlayComponentStack.tagOf(elementState);
        if (!Objects.equals(tag, obs$lastTag)) {
            // Do not merge elements of different components into one vertex batch.
            previousDraw = null;
            obs$lastTag = tag;
        }
    }

    @Inject(method = "addElementToMesh", at = @At("TAIL"))
    private void obs$afterElement(GuiElementRenderState elementState, CallbackInfo ci) {
        while (obs$drawTags.size() < draws.size()) {
            obs$drawTags.add(obs$lastTag);
        }
    }

    // Text glyphs and item atlas blits are emitted during prepare(), not during extraction, so the
    // component tag is re-applied around each individual entry.

    @WrapOperation(method = "prepareText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachText(Ljava/util/function/Consumer;)V"))
    private void obs$wrapText(GuiRenderState state, Consumer<GuiTextRenderState> consumer, Operation<Void> original) {
        original.call(state, (Consumer<GuiTextRenderState>) textState ->
                obs$runTagged(OverlayComponentStack.tagOf(textState), () -> consumer.accept(textState)));
    }

    @WrapOperation(method = "prepareItemElements",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachItem(Ljava/util/function/Consumer;)V"))
    private void obs$wrapItem(GuiRenderState state, Consumer<GuiItemRenderState> consumer, Operation<Void> original) {
        original.call(state, (Consumer<GuiItemRenderState>) itemState ->
                obs$runTagged(OverlayComponentStack.tagOf(itemState), () -> consumer.accept(itemState)));
    }

    @WrapOperation(method = "preparePictureInPicture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachPictureInPicture(Ljava/util/function/Consumer;)V"))
    private void obs$wrapPictureInPicture(GuiRenderState state, Consumer<PictureInPictureRenderState> consumer, Operation<Void> original) {
        original.call(state, (Consumer<PictureInPictureRenderState>) pipState ->
                obs$runTagged(OverlayComponentStack.tagOf(pipState), () -> consumer.accept(pipState)));
    }

    @Unique
    private static void obs$runTagged(Object tag, Runnable action) {
        OverlayComponentStack.pushTag(tag);
        try {
            action.run();
        } finally {
            OverlayComponentStack.pop();
        }
    }

    @Unique
    private Object obs$tagAt(int index) {
        return index >= 0 && index < obs$drawTags.size() ? obs$drawTags.get(index) : null;
    }

    /**
     * Reimplementation of {@code GuiRenderer#draw} that routes tagged ranges into the overlay
     * target. Kept in sync with the vanilla method (projection setup, blur boundary, depth clear).
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void obs$draw(CallbackInfo ci) {
        ci.cancel();
        List<?> currentDraws = this.draws;
        if (currentDraws.isEmpty()) {
            return;
        }
        int count = currentDraws.size();
        Minecraft minecraft = Minecraft.getInstance();
        WindowRenderState windowState = minecraft.gameRenderer.gameRenderState().windowRenderState;

        this.guiProjection.setupOrtho(1000.0F, 11000.0F,
                (float) windowState.width / windowState.guiScale,
                (float) windowState.height / windowState.guiScale, true);
        RenderSystem.setProjectionMatrix(this.guiProjectionMatrixBuffer.getBuffer(this.guiProjection), ProjectionType.ORTHOGRAPHIC);

        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F));

        OverlayRenderer renderer = OBSOverlay.getRenderer();
        boolean split = renderer != null && renderer.isActive();
        OverlayTarget overlay = split ? renderer.overlay() : null;
        boolean overlayUsable = overlay != null && overlay.target() != null;

        int blurIndex = Math.min(this.firstDrawIndexAfterBlur, count);
        boolean blurred = false;
        if (blurIndex == 0) {
            obs$blur(minecraft, mainTarget);
            blurred = true;
        }

        int index = 0;
        while (index < count) {
            if (!blurred && index == blurIndex) {
                obs$blur(minecraft, mainTarget);
                blurred = true;
            }
            Object tag = obs$tagAt(index);
            int end = index + 1;
            while (end < count && end != blurIndex && Objects.equals(obs$tagAt(end), tag)) {
                end++;
            }

            boolean toOverlay = split && overlayUsable && tag != null && tag != OverlayComponentStack.DROP;
            if (toOverlay) {
                overlay.markContent();
                obs$executeDrawRange(() -> "obs_overlay/gui overlay", overlay.target(), dynamicTransforms, index, end);
            } else if (tag != OverlayComponentStack.DROP) {
                obs$executeDrawRange(() -> "obs_overlay/gui main", mainTarget, dynamicTransforms, index, end);
            }
            // Dropped ranges (auto-hidden components) are simply not executed.
            index = end;
        }
    }

    @Unique
    private static void obs$blur(Minecraft minecraft, RenderTarget mainTarget) {
        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainTarget.getDepthTexture(), 0.0);
        minecraft.gameRenderer.processBlurEffect();
    }
}
