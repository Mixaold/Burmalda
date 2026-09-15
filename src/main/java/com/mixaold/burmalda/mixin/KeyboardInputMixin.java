package com.mixaold.burmalda.mixin;

import com.mixaold.burmalda.debuff.ClientDebuffState;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    // Three movement models across versions:
    //  - 1.21.1            : public pressing* booleans + movementSideways/Forward floats.
    //  - 1.21.2 / 1.21.3   : playerInput record + public movementSideways/Forward floats
    //                        (the floats drive actual velocity; movementVector does NOT exist yet).
    //  - 1.21.5+           : playerInput record + a protected Vec2f movementVector
    //                        (reached via InputAccessorMixin; the floats are gone).
    //? if >=1.21.5 {
    /*@org.spongepowered.asm.mixin.Unique
    private void burmalda$applyInvert() {
        KeyboardInput self = (KeyboardInput) (Object) this;
        net.minecraft.util.PlayerInput pi = self.playerInput;
        boolean fwd = pi.forward(), back = pi.backward(), left = pi.left(), right = pi.right();
        net.minecraft.util.math.Vec2f mv = ((InputAccessorMixin) (Object) this).burmalda$getMovementVector();
        float vx = mv.x, vy = mv.y;
        if (ClientDebuffState.invertHorizontalControls()) { boolean t = left; left = right; right = t; vx = -vx; }
        if (ClientDebuffState.invertForwardBackControls()) { boolean t = fwd; fwd = back; back = t; vy = -vy; }
        self.playerInput = new net.minecraft.util.PlayerInput(fwd, back, left, right, pi.jump(), pi.sneak(), pi.sprint());
        ((InputAccessorMixin) (Object) this).burmalda$setMovementVector(new net.minecraft.util.math.Vec2f(vx, vy));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void burmalda_invertControls(CallbackInfo ci) {
        burmalda$applyInvert();
    }
    *///? } else if >=1.21.2 {
    /*@org.spongepowered.asm.mixin.Unique
    private void burmalda$applyInvert() {
        KeyboardInput self = (KeyboardInput) (Object) this;
        net.minecraft.util.PlayerInput pi = self.playerInput;
        boolean fwd = pi.forward(), back = pi.backward(), left = pi.left(), right = pi.right();
        boolean ih = ClientDebuffState.invertHorizontalControls();
        boolean iv = ClientDebuffState.invertForwardBackControls();
        // The float fields already hold the (slowdown-scaled) magnitudes — just flip their sign.
        if (ih) { boolean t = left; left = right; right = t; self.movementSideways = -self.movementSideways; }
        if (iv) { boolean t = fwd; fwd = back; back = t; self.movementForward = -self.movementForward; }
        if (ih || iv) {
            self.playerInput = new net.minecraft.util.PlayerInput(fwd, back, left, right, pi.jump(), pi.sneak(), pi.sprint());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void burmalda_invertControls(boolean slowDown, float f, CallbackInfo ci) {
        burmalda$applyInvert();
    }
    *///? } else {
    @Inject(method = "tick", at = @At("TAIL"))
    private void burmalda_invertControls(boolean slowDown, float f, CallbackInfo ci) {
        KeyboardInput self = (KeyboardInput) (Object) this;

        if (ClientDebuffState.invertHorizontalControls()) {
            boolean left = self.pressingLeft;
            self.pressingLeft = self.pressingRight;
            self.pressingRight = left;

            // movementSideways is derived from pressingLeft/Right — recalculate
            float side = 0.0f;
            if (self.pressingLeft)  side += 1.0f;
            if (self.pressingRight) side -= 1.0f;
            self.movementSideways = slowDown ? side * 0.3f : side;
        }

        if (ClientDebuffState.invertForwardBackControls()) {
            boolean fwd = self.pressingForward;
            self.pressingForward = self.pressingBack;
            self.pressingBack = fwd;

            float forward = 0.0f;
            if (self.pressingForward) forward += 1.0f;
            if (self.pressingBack)    forward -= 1.0f;
            self.movementForward = slowDown ? forward * 0.3f : forward;
        }
    }
    //? }
}
