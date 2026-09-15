package com.mixaold.burmalda.mixin;

import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes ArmorStandEntity's private {@code setMarker(boolean)} so the floating
 * nametag stands can be made hitbox-less markers without the version-specific NBT
 * read path (NbtCompound pre-1.21.8 vs ReadView since 1.21.8).
 */
@Mixin(ArmorStandEntity.class)
public interface ArmorStandMarkerInvoker {
    @Invoker("setMarker")
    void burmalda$setMarker(boolean marker);
}
