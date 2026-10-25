package me.zziger.obsoverlay.nativehook;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface Kernel32 extends Library {
    Pointer GetModuleHandleA(String moduleName);

    Pointer GetProcAddress(Pointer module, String functionName);
}
