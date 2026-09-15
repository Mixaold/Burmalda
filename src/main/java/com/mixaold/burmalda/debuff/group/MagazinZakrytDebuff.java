package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class MagazinZakrytDebuff extends Debuff {

    public static boolean containersBlocked = false;

    public MagazinZakrytDebuff() {
        super("magazin_zakryt", "Store Closed",
                "All tools — chests, furnaces, workbenches — don't work, server is down...",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        containersBlocked = true;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatableWithFallback("burmalda.chat.magazin_zakryt.start", "§c[Store Closed] §fToo late. Everything is closed. Come back tomorrow."), false);
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        containersBlocked = false;
    }
}
