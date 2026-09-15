package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class AntigravitatsiyaDebuff extends Debuff {

    private static final int INTERVAL = 500; // 25 seconds

    public AntigravitatsiyaDebuff() {
        super("antigravitatsiya", "Anti-Gravity",
                "Launched upward every 25 seconds, sky is the limit",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;
        // Launch player upward
        player.setVelocity(player.getVelocity().x, 2.8, player.getVelocity().z);
        com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);
        BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.ANTIGRAVITY_LAUNCH);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.antigravity.liftoff", "§b[Anti-Gravity] §fLIFT OFF!"), true);
    }
}
