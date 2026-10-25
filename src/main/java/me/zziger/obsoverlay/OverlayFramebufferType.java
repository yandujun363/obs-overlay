package me.zziger.obsoverlay;

/**
 * Kind of overlay target, kept for API compatibility with the 1.21.x versions.
 *
 * <p>The 26.3 port only uses {@link #NORMAL}. {@link #DEPTH} existed for world-space content that
 * needed the main depth buffer; that whole feature set was dropped for 26.3 (see AGENTS.md).</p>
 */
public enum OverlayFramebufferType {
    NORMAL,
    DEPTH
}
