package com.mixaold.burmalda.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.chunk.ArrayPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ArrayPalette.class)
public class LinearPaletteMixin<T> {

    @Shadow private T[] array;

    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void safePaletteGet(int id, CallbackInfoReturnable<T> cir) {
        if (array != null && id >= array.length && array.length > 0) {
            cir.setReturnValue(array[array.length - 1]);
        }
    }
}
