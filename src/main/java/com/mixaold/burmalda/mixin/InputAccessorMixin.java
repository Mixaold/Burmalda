package com.mixaold.burmalda.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Accessor for {@code Input.movementVector} (the protected Vec2f that actually drives movement
 * since 1.21.5). It lives on the {@code Input} base class, so a @Shadow on the KeyboardInput
 * subclass can't reach it — this accessor targets the declaring class directly.
 *
 * NB: the field only exists from 1.21.5 onward. On 1.21.2/1.21.3 Input still uses the public
 * movementSideways/Forward floats, so this accessor must NOT be generated there (a required
 * mixin targeting a missing field aborts world-join as a fake "network protocol error").
 */
@Environment(EnvType.CLIENT)
@Mixin(Input.class)
public interface InputAccessorMixin {
    //? if >=1.21.5 {
    /*@org.spongepowered.asm.mixin.gen.Accessor("movementVector")
    net.minecraft.util.math.Vec2f burmalda$getMovementVector();

    @org.spongepowered.asm.mixin.gen.Accessor("movementVector")
    void burmalda$setMovementVector(net.minecraft.util.math.Vec2f vec);
    *///? }
}
