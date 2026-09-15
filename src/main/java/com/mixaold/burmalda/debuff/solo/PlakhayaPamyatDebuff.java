package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Random;

public class PlakhayaPamyatDebuff extends Debuff {

    private static final int INTERVAL = 900;
    private static final Random RANDOM = new Random();

    public PlakhayaPamyatDebuff() {
        super("plakhaya_pamyat", "Bad Memory",
                "Inventory shuffles every 45 seconds",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;
        for (int i = 35; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            net.minecraft.item.ItemStack tmp = player.getInventory().getStack(i);
            player.getInventory().setStack(i, player.getInventory().getStack(j));
            player.getInventory().setStack(j, tmp);
        }
        player.playerScreenHandler.syncState();
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.plakhaya_pamyat.shuffle", "§e[Bad Memory] §fWhere did it go..."), true);
    }
}
