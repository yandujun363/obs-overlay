package me.zziger.obsoverlay;

import me.zziger.obsoverlay.component.IOverlayComponent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tracks which overlay component is currently being extracted and remembers which
 * {@code GuiRenderState} entries belong to it.
 *
 * <p>The stack is only non-trivial during the extraction phase (render thread, single threaded),
 * which is why plain static state is fine here.</p>
 */
public final class OverlayComponentStack {
    /** Marker tag: the tagged content must not be drawn at all (e.g. auto-hidden while a screen is open). */
    public static final Object DROP = new Object() {
        @Override
        public String toString() {
            return "obs_overlay#drop";
        }
    };

    /** Tag used by the public API when a third party mod draws its own content. */
    public static final String API_TAG = "obs_overlay#api";

    private static Object[] stack = new Object[16];
    private static int stackSize;

    private static final Map<Object, Object> TAGS = new IdentityHashMap<>();

    private OverlayComponentStack() {
    }

    public static void push(IOverlayComponent component) {
        Object tag = null;
        if (component != null && component.isOverlayEnabled()) {
            tag = component.isHidden() ? DROP : component.getId();
        }
        pushTag(tag);
    }

    public static void pushTag(Object tag) {
        if (stackSize == stack.length) {
            Object[] grown = new Object[stack.length * 2];
            System.arraycopy(stack, 0, grown, 0, stack.length);
            stack = grown;
        }
        stack[stackSize++] = tag;
    }

    public static void pop() {
        if (stackSize > 0) {
            stack[--stackSize] = null;
        }
    }

    public static Object current() {
        return stackSize > 0 ? stack[stackSize - 1] : null;
    }

    public static void tag(Object state) {
        if (state == null) {
            return;
        }
        Object tag = current();
        if (tag != null) {
            TAGS.put(state, tag);
        }
    }

    public static Object tagOf(Object state) {
        return state == null ? null : TAGS.get(state);
    }

    /** Called once per frame; tags hold references to render states, so they must not outlive a frame. */
    public static void reset() {
        TAGS.clear();
        while (stackSize > 0) {
            stack[--stackSize] = null;
        }
    }
}
