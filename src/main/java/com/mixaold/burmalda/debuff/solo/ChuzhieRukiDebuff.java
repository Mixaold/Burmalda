package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ChuzhieRukiDebuff extends Debuff {

    public ChuzhieRukiDebuff() {
        super("chuzhie_ruki", "Someone Else's Hands",
                "RMB is LMB, LMB is RMB. Butterfingers",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.chuzhie_ruki.start", "§6[Someone Else's Hands] §fThe rules have changed."), false);
    }
}
