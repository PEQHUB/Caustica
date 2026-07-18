package dev.comfyfluffy.caustica.nrd;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/** Flat FFM bindings for the cross-platform Vulkan NRD bridge. */
public final class NrdLibrary {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final ValueLayout.OfInt I32 = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong I64 = ValueLayout.JAVA_LONG;

    private final MethodHandle version;
    private final MethodHandle lastError;
    private final MethodHandle create;
    private final MethodHandle evaluate;
    private final MethodHandle destroy;

    private NrdLibrary(SymbolLookup lookup) {
        version = handle(lookup, "nrdbridge_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
        lastError = handle(lookup, "nrdbridge_last_error", FunctionDescriptor.of(ValueLayout.ADDRESS));
        create = handle(lookup, "nrdbridge_create",
                FunctionDescriptor.of(I32, I64, I64, I64, I32, I32, I32, I32, I32));
        evaluate = handle(lookup, "nrdbridge_evaluate",
                FunctionDescriptor.of(I32, I64, I64, I32, I64, I64));
        destroy = handle(lookup, "nrdbridge_destroy", FunctionDescriptor.ofVoid());
    }

    public static NrdLibrary load(Path library) {
        return new NrdLibrary(SymbolLookup.libraryLookup(library, Arena.global()));
    }

    public String version() {
        return string(invokeAddress(version));
    }

    public String lastError() {
        return string(invokeAddress(lastError));
    }

    public int create(long instance, long physicalDevice, long device, int queueFamily,
            int width, int height, int family, boolean sphericalHarmonics) {
        return invokeInt(create, instance, physicalDevice, device, queueFamily, width, height,
                family, sphericalHarmonics ? 1 : 0);
    }

    public int evaluate(long commandBuffer, MemorySegment resources, int resourceCount,
            MemorySegment common, MemorySegment settings) {
        return invokeInt(evaluate, commandBuffer, resources.address(), resourceCount,
                common.address(), settings.address());
    }

    public void destroy() {
        try {
            destroy.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("NRD bridge destroy failed", throwable);
        }
    }

    private static MethodHandle handle(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = lookup.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Missing NRD bridge symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static int invokeInt(MethodHandle handle, Object... arguments) {
        try {
            return (int) handle.invokeWithArguments(arguments);
        } catch (Throwable throwable) {
            throw new IllegalStateException("NRD native call failed", throwable);
        }
    }

    private static MemorySegment invokeAddress(MethodHandle handle) {
        try {
            return (MemorySegment) handle.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("NRD native string call failed", throwable);
        }
    }

    private static String string(MemorySegment address) {
        return address == null || address.address() == 0L ? "" : address.reinterpret(4096).getString(0);
    }
}
