package me.zziger.obsoverlay.nativehook;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import me.zziger.obsoverlay.OBSOverlay;
import me.zziger.obsoverlay.OverlayRenderer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Installs a MinHook detour on {@code opengl32!wglSwapBuffers} (the same entry point OBS's game
 * capture hooks) so the overlay is composited after OBS copied the back buffer and before the
 * buffer is swapped.
 *
 * <p>Only that one entry point is hooked on purpose: hooking an earlier one
 * ({@code SDL_GL_SwapWindow}, {@code gdi32!SwapBuffers}) would draw before OBS's copy and leak the
 * hidden content into the capture. The ordering works out because our hook is installed while the
 * game starts and OBS installs its hook later, and MinHook prepends new detours, so OBS runs
 * first.</p>
 *
 * <p>Because a wrong guess here would make the HUD disappear from the screen entirely, the renderer
 * only starts splitting content off after this hook has actually fired at least once
 * ({@link #hasFired()}).</p>
 */
public final class NativeHook {
    private static final String MODULE = "opengl32.dll";
    private static final String EXPORT = "wglSwapBuffers";

    private final OverlayRenderer renderer;

    private MinHook minHook;
    private MinHook.SwapBuffers swapCallback;
    private Function originalSwap;
    private boolean hooked;
    private volatile boolean fired;
    private int lastFrame = -1;
    private boolean loggedFailure;

    public NativeHook(OverlayRenderer renderer) {
        this.renderer = renderer;
    }

    public boolean isHooked() {
        return hooked;
    }

    /** True once the detour has been invoked, i.e. the compositor is known to run. */
    public boolean hasFired() {
        return fired;
    }

    public void init() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            OBSOverlay.LOGGER.warn("[obs_overlay] not running on Windows, the native present hook is unavailable");
            return;
        }
        if (arch.contains("aarch")) {
            OBSOverlay.LOGGER.warn("[obs_overlay] ARM Windows is not supported by the bundled MinHook build");
            return;
        }
        boolean x64 = arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64") || arch.equals("ia64");

        Path library = extractMinHook(x64);
        if (library == null) {
            return;
        }
        String existing = System.getProperty("jna.library.path", "");
        System.setProperty("jna.library.path",
                library.getParent().toAbsolutePath() + (existing.isEmpty() ? "" : File.pathSeparator + existing));

        Kernel32 kernel32 = Native.load("kernel32", Kernel32.class);
        Pointer module = kernel32.GetModuleHandleA(MODULE);
        if (module == null) {
            OBSOverlay.LOGGER.warn("[obs_overlay] {} is not loaded, cannot install the present hook", MODULE);
            return;
        }
        Pointer target = kernel32.GetProcAddress(module, EXPORT);
        if (target == null) {
            OBSOverlay.LOGGER.warn("[obs_overlay] {}!{} not found", MODULE, EXPORT);
            return;
        }

        try {
            minHook = Native.load("MinHook", MinHook.class);
            int status = minHook.MH_Initialize();
            if (status != 0) {
                OBSOverlay.LOGGER.error("[obs_overlay] MH_Initialize failed with status {}", status);
                return;
            }

            PointerByReference original = new PointerByReference();
            this.swapCallback = this::onSwap;
            status = minHook.MH_CreateHook(target, swapCallback, original);
            if (status != 0) {
                OBSOverlay.LOGGER.error("[obs_overlay] MH_CreateHook failed with status {}", status);
                return;
            }
            Pointer originalPointer = original.getValue();
            if (originalPointer == null) {
                OBSOverlay.LOGGER.error("[obs_overlay] MH_CreateHook returned no original function");
                return;
            }
            this.originalSwap = Function.getFunction(originalPointer, Function.ALT_CONVENTION);
            status = minHook.MH_EnableHook(target);
            if (status != 0) {
                OBSOverlay.LOGGER.error("[obs_overlay] MH_EnableHook failed with status {}", status);
                return;
            }
            hooked = true;
            OBSOverlay.LOGGER.info("[obs_overlay] native present hook installed on {}!{}", MODULE, EXPORT);
        } catch (Throwable throwable) {
            OBSOverlay.LOGGER.error("[obs_overlay] could not install the MinHook detour", throwable);
        }
    }

    private Path extractMinHook(boolean x64) {
        String resource = x64 ? "/lib/MinHook.x64.dll" : "/lib/MinHook.x86.dll";
        Path directory = FabricLoader.getInstance().getGameDir().resolve("obs_overlay").resolve("native");
        // JNA resolves the logical name "MinHook" to MinHook.dll, so this exact name is required.
        Path destination = directory.resolve("MinHook.dll");
        try (InputStream input = NativeHook.class.getResourceAsStream(resource)) {
            if (input == null) {
                OBSOverlay.LOGGER.error("[obs_overlay] native library {} is missing from the jar", resource);
                return null;
            }
            Files.createDirectories(directory);
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException exception) {
            OBSOverlay.LOGGER.error("[obs_overlay] could not extract the MinHook library", exception);
            return null;
        }
    }

    private boolean onSwap(Pointer firstArgument) {
        fired = true;
        int frame = renderer.frameId();
        if (frame != lastFrame) {
            lastFrame = frame;
            try {
                GlCompositor.composite(renderer);
            } catch (Throwable throwable) {
                if (!loggedFailure) {
                    loggedFailure = true;
                    OBSOverlay.LOGGER.error("[obs_overlay] compositing the overlay failed", throwable);
                }
            }
        }
        return (Boolean) originalSwap.invoke(Boolean.class, new Object[]{firstArgument});
    }
}
