package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Knockback multiplier: handled by PlayerEntityMixin via DebuffManager check. */
public class TryapichnayaKuklaDebuff extends Debuff {

    public TryapichnayaKuklaDebuff() {
        super("tryapichnaya_kukla", "Ragdoll",
                "Knockback from any damage is 10x stronger",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.tryapichnaya_kukla.start", "§6[Ragdoll] §fYour body is like a ragdoll now."), false);
    }
}
