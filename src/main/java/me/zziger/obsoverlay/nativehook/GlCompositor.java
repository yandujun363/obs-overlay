package me.zziger.obsoverlay.nativehook;

import me.zziger.obsoverlay.OBSOverlay;
import me.zziger.obsoverlay.OBSOverlayConfig;
import me.zziger.obsoverlay.OverlayRenderer;
import me.zziger.obsoverlay.OverlayTarget;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Draws the overlay target onto the window back buffer using plain OpenGL (3.3 core, which is what
 * the renderpearl OpenGL backend creates).
 *
 * <p>This runs inside the native present detour, i.e. outside of any renderpearl render pass, so it
 * cannot use the engine's abstractions. Every piece of GL state touched here is saved and restored
 * afterwards: renderpearl keeps its own state cache and would otherwise emit wrong state for the
 * next frame.</p>
 */
public final class GlCompositor {
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;
    private static final int GL_BLEND_EQUATION_RGB = 0x8009;
    private static final int GL_BLEND_EQUATION_ALPHA = 0x883D;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_SAMPLER_BINDING = 0x8919;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aTexture;
            out vec2 vTexture;
            void main() {
                vTexture = aTexture;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vTexture;
            out vec4 fragColor;
            uniform sampler2D uTexture;
            void main() {
                fragColor = texture(uTexture, vTexture);
            }
            """;

    private static boolean initialised;
    private static boolean failed;
    /** Set when compositing cannot work (no GL texture handle, shader failure, ...). */
    private static volatile boolean broken;
    private static int program = -1;
    private static int vao = -1;
    private static int vbo = -1;
    private static int uTexture = -1;
    private static boolean warnedNoTexture;
    private static boolean warnedNotGl;

    private GlCompositor() {
    }

    /** True when compositing cannot work; the renderer then stops splitting content into the overlay. */
    public static boolean isBroken() {
        return broken;
    }

    public static void composite(OverlayRenderer renderer) {
        if (!renderer.isActive()) {
            return;
        }
        OverlayTarget overlay = renderer.overlay();
        if (overlay == null || !overlay.hasContent()) {
            return;
        }
        int texture = overlay.glTextureId();
        if (texture == 0) {
            if (!warnedNotGl) {
                warnedNotGl = true;
                OBSOverlay.LOGGER.warn("[obs_overlay] overlay texture is not an OpenGL texture"
                        + " (Vulkan backend active?) - nothing can be composited, disabling the overlay"
                        + " split so the HUD stays visible");
            }
            broken = true;
            return;
        }
        if (!ensureInitialised()) {
            return;
        }
        if (overlay.width() <= 0 || overlay.height() <= 0) {
            return;
        }
        draw(texture, overlay.width(), overlay.height());
    }

    private static boolean ensureInitialised() {
        if (initialised) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            if (GL.getCapabilities() == null) {
                throw new IllegalStateException("no GL capabilities");
            }
            program = linkProgram();
            uTexture = GL20C.glGetUniformLocation(program, "uTexture");

            vao = GL30C.glGenVertexArrays();
            vbo = GL15C.glGenBuffers();
            GL30C.glBindVertexArray(vao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                float[] vertices = {
                        -1.0F, -1.0F, 0.0F, 0.0F,
                        1.0F, -1.0F, 1.0F, 0.0F,
                        -1.0F, 1.0F, 0.0F, 1.0F,
                        1.0F, 1.0F, 1.0F, 1.0F
                };
                GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, stack.floats(vertices), GL15C.GL_STATIC_DRAW);
            }
            GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, 4 * Float.BYTES, 0L);
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
            GL20C.glEnableVertexAttribArray(1);
            GL30C.glBindVertexArray(0);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

            initialised = true;
            OBSOverlay.LOGGER.info("[obs_overlay] GL compositor ready ({})", GL11C.glGetString(GL11C.GL_VERSION));
            return true;
        } catch (Throwable throwable) {
            failed = true;
            broken = true;
            OBSOverlay.LOGGER.error("[obs_overlay] could not initialise the GL compositor, disabling the overlay split", throwable);
            return false;
        }
    }

    private static int linkProgram() {
        int vertexShader = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int shaderProgram = GL20C.glCreateProgram();
        GL20C.glAttachShader(shaderProgram, vertexShader);
        GL20C.glAttachShader(shaderProgram, fragmentShader);
        GL20C.glLinkProgram(shaderProgram);
        if (GL20C.glGetProgrami(shaderProgram, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("link failed: " + GL20C.glGetProgramInfoLog(shaderProgram));
        }
        GL20C.glDeleteShader(vertexShader);
        GL20C.glDeleteShader(fragmentShader);
        return shaderProgram;
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("compile failed: " + GL20C.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static void draw(int texture, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int previousProgram = GL11C.glGetInteger(GL_CURRENT_PROGRAM);
            int previousVao = GL11C.glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int previousDrawFbo = GL11C.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int previousReadFbo = GL11C.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int previousSrcRgb = GL11C.glGetInteger(GL_BLEND_SRC_RGB);
            int previousDstRgb = GL11C.glGetInteger(GL_BLEND_DST_RGB);
            int previousSrcAlpha = GL11C.glGetInteger(GL_BLEND_SRC_ALPHA);
            int previousDstAlpha = GL11C.glGetInteger(GL_BLEND_DST_ALPHA);
            int previousEquationRgb = GL11C.glGetInteger(GL_BLEND_EQUATION_RGB);
            int previousEquationAlpha = GL11C.glGetInteger(GL_BLEND_EQUATION_ALPHA);

            IntBuffer viewport = stack.mallocInt(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            ByteBuffer depthMask = stack.malloc(1);
            GL11C.glGetBooleanv(GL11C.GL_DEPTH_WRITEMASK, depthMask);

            boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
            boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
            boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
            boolean scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);

            // Texture binding and sampler are per texture unit: capture them on unit 0 itself.
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            int previousSampler = GL11C.glGetInteger(GL_SAMPLER_BINDING);

            try {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
                GL11C.glViewport(0, 0, width, height);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glEnable(GL11C.GL_BLEND);
                GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
                boolean premultiplied = OBSOverlayConfig.get() == null || OBSOverlayConfig.get().premultipliedAlpha;
                int sourceFactor = premultiplied ? GL11C.GL_ONE : GL11C.GL_SRC_ALPHA;
                GL14C.glBlendFuncSeparate(sourceFactor, GL11C.GL_ONE_MINUS_SRC_ALPHA,
                        GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);
                GL11C.glColorMask(true, true, true, true);
                GL11C.glDepthMask(false);

                GL20C.glUseProgram(program);
                GL30C.glBindVertexArray(vao);
                // A sampler object bound to the unit would override the texture parameters, and the
                // texture itself may still have the GL default (mipmap) min filter, which makes
                // sampling a single-mip texture incomplete. Unbind the sampler and set filters.
                GL33C.glBindSampler(0, 0);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
                if (uTexture >= 0) {
                    GL20C.glUniform1i(uTexture, 0);
                }
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, 0, 4);
            } finally {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
                GL33C.glBindSampler(0, previousSampler);
                GL13C.glActiveTexture(previousActiveTexture);
                GL30C.glBindVertexArray(previousVao);
                GL20C.glUseProgram(previousProgram);
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11C.glDepthMask(depthMask.get(0) != 0);
                GL20C.glBlendEquationSeparate(previousEquationRgb, previousEquationAlpha);
                GL14C.glBlendFuncSeparate(previousSrcRgb, previousDstRgb, previousSrcAlpha, previousDstAlpha);
                setEnabled(GL11C.GL_BLEND, blendEnabled);
                setEnabled(GL11C.GL_DEPTH_TEST, depthEnabled);
                setEnabled(GL11C.GL_CULL_FACE, cullEnabled);
                setEnabled(GL11C.GL_SCISSOR_TEST, scissorEnabled);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFbo);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFbo);
            }
        } catch (Throwable throwable) {
            if (!warnedNoTexture) {
                warnedNoTexture = true;
                OBSOverlay.LOGGER.error("[obs_overlay] failed to draw the overlay", throwable);
            }
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11C.glEnable(capability);
        } else {
            GL11C.glDisable(capability);
        }
    }
}
