package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.client.CausticaKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Keeps vanilla restart reporting limited to settings that genuinely require a restart. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;",
            shift = At.Shift.AFTER))
    private void caustica$addKeyMappings(CallbackInfo ci) {
        KeyMapping[] causticaMappings = CausticaKeyMappings.all();
        int originalLength = this.keyMappings.length;
        this.keyMappings = Arrays.copyOf(this.keyMappings, originalLength + causticaMappings.length);
        System.arraycopy(causticaMappings, 0, this.keyMappings, originalLength, causticaMappings.length);
    }
}
