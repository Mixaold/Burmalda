package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Client-side effect: hotbar and HP hidden via InGameHudMixin reading ClientDebuffState. */
public class SlepoeDoverieDebuff extends Debuff {

    public SlepoeDoverieDebuff() {
        super("slepoe_doverie", "Blind Trust",
                "HP and hotbar hidden — live by feel",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.slepoe_doverie.start", "§7[Blind Trust] §fHotbar and HP hidden. Improvise."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.slepoe_doverie.end", "§7[Blind Trust] §fEverything visible now."), false);
    }
}
