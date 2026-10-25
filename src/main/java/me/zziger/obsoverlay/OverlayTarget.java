package me.zziger.obsoverlay;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Off-screen target the "hidden" GUI content is rendered into.
 *
 * <p>Same colour/depth formats as {@code MainTarget} so the GUI pipelines behave identically.</p>
 */
public final class OverlayTarget {
    public static final Vector4f CLEAR_COLOR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private final OverlayFramebufferType type;
    private TextureTarget target;
    private int width;
    private int height;
    private boolean content;

    public OverlayTarget(OverlayFramebufferType type) {
        this.type = type;
    }

    public RenderTarget target() {
        return target;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void ensureSize(int newWidth, int newHeight) {
        RenderSystem.assertOnRenderThread();
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }
        if (target != null && (width != newWidth || height != newHeight)) {
            target.destroyBuffers();
            target = null;
        }
        if (target == null) {
            target = new TextureTarget("obs_overlay/" + type.name().toLowerCase(Locale.ROOT),
                    newWidth, newHeight, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            width = newWidth;
            height = newHeight;
        }
    }

    public void clear() {
        content = false;
        if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null) {
            return;
        }
        RenderSystem.getDevice()
                .createCommandEncoder()
                .clearColorAndDepthTextures(target.getColorTexture(), CLEAR_COLOR, target.getDepthTexture(), 0.0);
    }

    public boolean hasContent() {
        return content;
    }

    public void markContent() {
        content = true;
    }

    /** The raw OpenGL texture handle, or 0 when the current backend is not OpenGL. */
    public int glTextureId() {
        if (target == null) {
            return 0;
        }
        GpuTexture color = target.getColorTexture();
        if (color instanceof com.mojang.renderpearl.backend.opengl.GlTexture glTexture) {
            return glTexture.glId();
        }
        return 0;
    }
}
