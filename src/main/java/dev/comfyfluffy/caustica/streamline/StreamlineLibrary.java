package dev.comfyfluffy.caustica.streamline;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Flat FFM bindings for Caustica's native Streamline bridge. */
public final class StreamlineLibrary {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final ValueLayout.OfInt I32 = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong I64 = ValueLayout.JAVA_LONG;

    private final MethodHandle initialize;
    private final MethodHandle getAbiInfo;
    private final MethodHandle shutdown;
    private final MethodHandle isInitialized;
    private final MethodHandle lastResult;
    private final MethodHandle lastError;
    private final MethodHandle pollApiError;
    private final MethodHandle getTraceState;
    private final MethodHandle vkCreateInstance;
    private final MethodHandle vkEnumeratePhysicalDevices;
    private final MethodHandle vkCreateDevice;
    private final MethodHandle vkCreateWin32Surface;
    private final MethodHandle vkDestroySurface;
    private final MethodHandle vkCreateSwapchain;
    private final MethodHandle vkDestroySwapchain;
    private final MethodHandle vkGetSwapchainImages;
    private final MethodHandle vkAcquireNextImage;
    private final MethodHandle vkQueuePresent;
    private final MethodHandle vkDeviceWaitIdle;
    private final MethodHandle vkWaitTimeline;
    private final MethodHandle supportsFeature;
    private final MethodHandle getFeatureRequirements;
    private final MethodHandle getFeatureVersion;
    private final MethodHandle setFeatureLoaded;
    private final MethodHandle isFeatureLoaded;
    private final MethodHandle beginFrame;
    private final MethodHandle setConstants;
    private final MethodHandle tagResources;
    private final MethodHandle setDlssgOptions;
    private final MethodHandle getDlssgState;
    private final MethodHandle getDlssdOptimalSettings;
    private final MethodHandle setDlssdOptions;
    private final MethodHandle evaluateDlssd;
    private final MethodHandle freeDlssdResources;
    private final MethodHandle setReflexOptions;
    private final MethodHandle reflexSleep;
    private final MethodHandle pclSetMarker;
    private final MethodHandle getReflexState;

    private StreamlineLibrary(SymbolLookup lookup) {
        initialize = handle(lookup, "slbridge_initialize", FunctionDescriptor.of(I32,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, I32, I32));
        getAbiInfo = handle(lookup, "slbridge_get_abi_info", FunctionDescriptor.of(I32, I64));
        shutdown = handle(lookup, "slbridge_shutdown", FunctionDescriptor.of(I32));
        isInitialized = handle(lookup, "slbridge_is_initialized", FunctionDescriptor.of(I32));
        lastResult = handle(lookup, "slbridge_last_result", FunctionDescriptor.of(I32));
        lastError = handle(lookup, "slbridge_last_error", FunctionDescriptor.of(ValueLayout.ADDRESS));
        pollApiError = handle(lookup, "slbridge_poll_api_error", FunctionDescriptor.of(I32, I64));
        getTraceState = handle(lookup, "slbridge_get_trace_state", FunctionDescriptor.of(I32, I64));

        vkCreateInstance = handle(lookup, "slbridge_vk_create_instance", FunctionDescriptor.of(I32, I64, I64, I64));
        vkEnumeratePhysicalDevices = handle(lookup, "slbridge_vk_enumerate_physical_devices",
                FunctionDescriptor.of(I32, I64, I64, I64));
        vkCreateDevice = handle(lookup, "slbridge_vk_create_device", FunctionDescriptor.of(I32, I64, I64, I64, I64));
        vkCreateWin32Surface = handle(lookup, "slbridge_vk_create_win32_surface", FunctionDescriptor.of(I32, I64, I64, I64, I64));
        vkDestroySurface = handle(lookup, "slbridge_vk_destroy_surface", FunctionDescriptor.ofVoid(I64, I64, I64));
        vkCreateSwapchain = handle(lookup, "slbridge_vk_create_swapchain", FunctionDescriptor.of(I32, I64, I64, I64, I64));
        vkDestroySwapchain = handle(lookup, "slbridge_vk_destroy_swapchain", FunctionDescriptor.ofVoid(I64, I64, I64));
        vkGetSwapchainImages = handle(lookup, "slbridge_vk_get_swapchain_images", FunctionDescriptor.of(I32, I64, I64, I64, I64));
        vkAcquireNextImage = handle(lookup, "slbridge_vk_acquire_next_image", FunctionDescriptor.of(I32, I64, I64, I64, I64, I64, I64));
        vkQueuePresent = handle(lookup, "slbridge_vk_queue_present", FunctionDescriptor.of(I32, I64, I64));
        vkDeviceWaitIdle = handle(lookup, "slbridge_vk_device_wait_idle", FunctionDescriptor.of(I32, I64));
        vkWaitTimeline = handle(lookup, "slbridge_vk_wait_timeline", FunctionDescriptor.of(I32, I64, I64, I64, I64));

        supportsFeature = handle(lookup, "slbridge_supports_feature", FunctionDescriptor.of(I32, I32, I64));
        getFeatureRequirements = handle(lookup, "slbridge_get_feature_requirements", FunctionDescriptor.of(I32, I32, I64));
        getFeatureVersion = handle(lookup, "slbridge_get_feature_version", FunctionDescriptor.of(I32, I32, I64));
        setFeatureLoaded = handle(lookup, "slbridge_set_feature_loaded", FunctionDescriptor.of(I32, I32, I32));
        isFeatureLoaded = handle(lookup, "slbridge_is_feature_loaded", FunctionDescriptor.of(I32, I32));

        beginFrame = handle(lookup, "slbridge_begin_frame", FunctionDescriptor.of(I32, I32, I64));
        setConstants = handle(lookup, "slbridge_set_constants", FunctionDescriptor.of(I32, I64, I32, I64));
        tagResources = handle(lookup, "slbridge_tag_resources", FunctionDescriptor.of(I32, I64, I32, I64, I32, I64));
        setDlssgOptions = handle(lookup, "slbridge_set_dlssg_options", FunctionDescriptor.of(I32, I32, I64));
        getDlssgState = handle(lookup, "slbridge_get_dlssg_state", FunctionDescriptor.of(I32, I32, I64, I64));
        getDlssdOptimalSettings = handle(lookup, "slbridge_get_dlssd_optimal_settings",
                FunctionDescriptor.of(I32, I32, I32, I32, I64, I64, I64));
        setDlssdOptions = handle(lookup, "slbridge_set_dlssd_options",
                FunctionDescriptor.of(I32, I32, I64));
        evaluateDlssd = handle(lookup, "slbridge_evaluate_dlssd",
                FunctionDescriptor.of(I32, I64, I32, I64, I32, I64, I64));
        freeDlssdResources = handle(lookup, "slbridge_free_dlssd_resources",
                FunctionDescriptor.of(I32, I32));
        setReflexOptions = handle(lookup, "slbridge_set_reflex_options", FunctionDescriptor.of(I32, I64));
        reflexSleep = handle(lookup, "slbridge_reflex_sleep", FunctionDescriptor.of(I32, I64));
        pclSetMarker = handle(lookup, "slbridge_pcl_set_marker", FunctionDescriptor.of(I32, I32, I64));
        getReflexState = handle(lookup, "slbridge_get_reflex_state", FunctionDescriptor.of(I32, I64));
    }

    public static StreamlineLibrary load(Path dll) {
        return new StreamlineLibrary(SymbolLookup.libraryLookup(dll, Arena.global()));
    }

    public int initialize(MemorySegment pluginDirectory, MemorySegment logDirectory, int applicationId, int variant) {
        return invokeInt(initialize, pluginDirectory, logDirectory, applicationId, variant);
    }

    public int getAbiInfo(MemorySegment output) {
        return invokeInt(getAbiInfo, output.address());
    }

    public int shutdown() {
        return invokeInt(shutdown);
    }

    public boolean isInitialized() {
        return invokeInt(isInitialized) != 0;
    }

    public int lastResult() {
        return invokeInt(lastResult);
    }

    public String lastError() {
        long address = invokeAddress(lastError);
        if (address == 0L) {
            return "";
        }
        return MemorySegment.ofAddress(address).reinterpret(4096).getString(0);
    }

    public int pollApiError(MemorySegment outputVkResult) {
        return invokeInt(pollApiError, outputVkResult.address());
    }

    public int getTraceState(MemorySegment output) {
        return invokeInt(getTraceState, output.address());
    }

    public int vkCreateInstance(long createInfo, long allocator, long instanceOut) {
        return invokeInt(vkCreateInstance, createInfo, allocator, instanceOut);
    }

    public int vkEnumeratePhysicalDevices(long instance, long count, long physicalDevices) {
        return invokeInt(vkEnumeratePhysicalDevices, instance, count, physicalDevices);
    }

    public int vkCreateDevice(long physicalDevice, long createInfo, long allocator, long deviceOut) {
        return invokeInt(vkCreateDevice, physicalDevice, createInfo, allocator, deviceOut);
    }

    public int vkCreateWin32Surface(long instance, long hwnd, long allocator, long surfaceOut) {
        return invokeInt(vkCreateWin32Surface, instance, hwnd, allocator, surfaceOut);
    }

    public void vkDestroySurface(long instance, long surface, long allocator) {
        invokeVoid(vkDestroySurface, instance, surface, allocator);
    }

    public int vkCreateSwapchain(long device, long createInfo, long allocator, long swapchainOut) {
        return invokeInt(vkCreateSwapchain, device, createInfo, allocator, swapchainOut);
    }

    public void vkDestroySwapchain(long device, long swapchain, long allocator) {
        invokeVoid(vkDestroySwapchain, device, swapchain, allocator);
    }

    public int vkGetSwapchainImages(long device, long swapchain, long count, long images) {
        return invokeInt(vkGetSwapchainImages, device, swapchain, count, images);
    }

    public int vkAcquireNextImage(long device, long swapchain, long timeout, long semaphore, long fence, long imageIndex) {
        return invokeInt(vkAcquireNextImage, device, swapchain, timeout, semaphore, fence, imageIndex);
    }

    public int vkQueuePresent(long queue, long presentInfo) {
        return invokeInt(vkQueuePresent, queue, presentInfo);
    }

    public int vkDeviceWaitIdle(long device) {
        return invokeInt(vkDeviceWaitIdle, device);
    }

    public int vkWaitTimeline(long device, long semaphore, long value, long timeoutNs) {
        return invokeInt(vkWaitTimeline, device, semaphore, value, timeoutNs);
    }

    public int supportsFeature(int feature, long physicalDevice) {
        return invokeInt(supportsFeature, feature, physicalDevice);
    }

    public int getFeatureRequirements(int feature, MemorySegment output) {
        return invokeInt(getFeatureRequirements, feature, output.address());
    }

    public int getFeatureVersion(int feature, MemorySegment output) {
        return invokeInt(getFeatureVersion, feature, output.address());
    }

    public int setFeatureLoaded(int feature, boolean loaded) {
        return invokeInt(setFeatureLoaded, feature, loaded ? 1 : 0);
    }

    public boolean isFeatureLoaded(int feature) {
        return invokeInt(isFeatureLoaded, feature) != 0;
    }

    public long beginFrame(int frameIndex, MemorySegment outputToken) {
        int result = invokeInt(beginFrame, frameIndex, outputToken.address());
        return result == 0 ? outputToken.get(I64, 0) : 0L;
    }

    public int setConstants(long frameToken, int viewport, MemorySegment constants) {
        return invokeInt(setConstants, frameToken, viewport, constants.address());
    }

    public int tagResources(long frameToken, int viewport, MemorySegment resources, int count, long commandBuffer) {
        return invokeInt(tagResources, frameToken, viewport, resources.address(), count, commandBuffer);
    }

    public int setDlssgOptions(int viewport, MemorySegment options) {
        return invokeInt(setDlssgOptions, viewport, options.address());
    }

    public int getDlssgState(int viewport, MemorySegment output) {
        return invokeInt(getDlssgState, viewport, output.address(), 0L);
    }

    public int getDlssgState(int viewport, MemorySegment output, MemorySegment estimateOptions) {
        return invokeInt(getDlssgState, viewport, output.address(),
                estimateOptions == null ? 0L : estimateOptions.address());
    }

    public int getDlssdOptimalSettings(int mode, int outputWidth, int outputHeight,
            MemorySegment renderWidth, MemorySegment renderHeight, MemorySegment sharpness) {
        return invokeInt(getDlssdOptimalSettings, mode, outputWidth, outputHeight,
                renderWidth.address(), renderHeight.address(), sharpness.address());
    }

    public int setDlssdOptions(int viewport, MemorySegment options) {
        return invokeInt(setDlssdOptions, viewport, options.address());
    }

    public int evaluateDlssd(long frameToken, int viewport, MemorySegment resources, int resourceCount,
            MemorySegment constants, long commandBuffer) {
        return invokeInt(evaluateDlssd, frameToken, viewport, resources.address(), resourceCount,
                constants.address(), commandBuffer);
    }

    public int freeDlssdResources(int viewport) {
        return invokeInt(freeDlssdResources, viewport);
    }

    public int setReflexOptions(MemorySegment options) {
        return invokeInt(setReflexOptions, options.address());
    }

    public int reflexSleep(long frameToken) {
        return invokeInt(reflexSleep, frameToken);
    }

    public int pclSetMarker(int marker, long frameToken) {
        return invokeInt(pclSetMarker, marker, frameToken);
    }

    public int getReflexState(MemorySegment output) {
        return invokeInt(getReflexState, output.address());
    }

    static MemorySegment utf16(Arena arena, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment result = arena.allocate(bytes.length + 2L, 2);
        MemorySegment.copy(bytes, 0, result, ValueLayout.JAVA_BYTE, 0, bytes.length);
        result.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
        result.set(ValueLayout.JAVA_BYTE, bytes.length + 1L, (byte) 0);
        return result;
    }

    private static MethodHandle handle(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.find(name)
                .orElseThrow(() -> new IllegalStateException("streamlinebridge missing export " + name)), descriptor);
    }

    private static int invokeInt(MethodHandle handle, Object... arguments) {
        try {
            return (int) handle.invokeWithArguments(arguments);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Streamline bridge call failed", throwable);
        }
    }

    private static long invokeAddress(MethodHandle handle) {
        try {
            return ((MemorySegment) handle.invokeExact()).address();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Streamline bridge call failed", throwable);
        }
    }

    private static void invokeVoid(MethodHandle handle, Object... arguments) {
        try {
            handle.invokeWithArguments(arguments);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Streamline bridge call failed", throwable);
        }
    }
}
