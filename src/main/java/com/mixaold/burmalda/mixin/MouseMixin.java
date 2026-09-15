package com.mixaold.burmalda.mixin;

import com.mixaold.burmalda.debuff.ClientDebuffState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class MouseMixin {

    private static InputUtil.Key swapped(InputUtil.Key key) {
        int code = key.getCode();
        if (code == 0) return InputUtil.Type.MOUSE.createFromCode(1);
        if (code == 1) return InputUtil.Type.MOUSE.createFromCode(0);
        return key;
    }

    // Свапаем "is pressed" состояние
    @Redirect(method = "onMouseButton",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/option/KeyBinding;setKeyPressed(Lnet/minecraft/client/util/InputUtil$Key;Z)V"))
    private void burmalda_swapMouseButtons(InputUtil.Key key, boolean pressed) {
        KeyBinding.setKeyPressed(ClientDebuffState.swapMouseButtons() ? swapped(key) : key, pressed);
    }

    // Свапаем "was pressed" счётчик — без этого блоки всё равно ставятся
    @Redirect(method = "onMouseButton",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/option/KeyBinding;onKeyPressed(Lnet/minecraft/client/util/InputUtil$Key;)V"))
    private void burmalda_swapMouseButtonsOnPress(InputUtil.Key key) {
        KeyBinding.onKeyPressed(ClientDebuffState.swapMouseButtons() ? swapped(key) : key);
    }
}
