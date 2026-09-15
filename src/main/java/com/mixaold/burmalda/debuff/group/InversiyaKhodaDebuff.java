package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Client-side effect: W/S inversion via KeyboardInputMixin reading ClientDebuffState.invertForwardBackControls() */
public class InversiyaKhodaDebuff extends Debuff {

    public InversiyaKhodaDebuff() {
        super("inversiya_khoda", "Movement Inversion",
                "Forward/back inverted for all",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatableWithFallback("burmalda.chat.inversiya_khoda.start", "§d[Movement Inversion] §fW is S. S is W. Forward goes backward."), false);
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatableWithFallback("burmalda.chat.inversiya_khoda.end", "§d[Movement Inversion] §fWorld is normal again."), false);
        }
    }
}
