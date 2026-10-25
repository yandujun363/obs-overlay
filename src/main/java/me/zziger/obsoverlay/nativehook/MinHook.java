package me.zziger.obsoverlay.nativehook;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

public interface MinHook extends StdCallLibrary {
    interface SwapBuffers extends StdCallCallback {
        boolean callback(Pointer firstArgument);
    }

    int MH_Initialize();

    int MH_CreateHook(Pointer target, SwapBuffers detour, PointerByReference original);

    int MH_EnableHook(Pointer target);

    int MH_DisableHook(Pointer target);

    int MH_Uninitialize();
}
