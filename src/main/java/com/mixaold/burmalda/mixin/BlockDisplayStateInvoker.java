package com.mixaold.burmalda.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes BlockDisplayEntity's private {@code setBlockState} so drone parts can pick their block in code. */
@Mixin(DisplayEntity.BlockDisplayEntity.class)
public interface BlockDisplayStateInvoker {
    @Invoker("setBlockState")
    void burmalda$setBlockState(BlockState state);
}
