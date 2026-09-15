package com.mixaold.burmalda.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes DisplayEntity's private setters so the Shahed drone visual can position/scale/spin
 * its block parts in code (these are otherwise only reachable via /summon NBT). The method
 * names are identical on 1.21.1–1.21.11, so the @Invoker remaps cleanly per version.
 */
@Mixin(DisplayEntity.class)
public interface DisplayTransformInvoker {
    @Invoker("setTransformation")
    void burmalda$setTransformation(AffineTransformation transformation);

    @Invoker("setTeleportDuration")
    void burmalda$setTeleportDuration(int ticks);

    @Invoker("setBrightness")
    void burmalda$setBrightness(Brightness brightness);
}
