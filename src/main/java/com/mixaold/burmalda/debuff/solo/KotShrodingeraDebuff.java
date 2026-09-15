package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Client-side effect: HP icons hidden via InGameHudMixin reading ClientDebuffState.hideHealth(). */
public class KotShrodingeraDebuff extends Debuff {

    public KotShrodingeraDebuff() {
        super("kot_shrodingera", "Schrödinger's Cat",
                "HP hidden — dead soon or not, who knows",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.kot_shrodingera.start", "§5[Schrödinger's Cat] §fAlive or dead? Great question."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.kot_shrodingera.end", "§5[Schrödinger's Cat] §fHP visible again. You're not dead. Yet."), false);
    }
}
