package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;

public class ObshchagaDebuff extends Debuff {

    public ObshchagaDebuff() {
        super("obshchaga", "Dorm Room",
                "Linked to 2 players: shared damage and inventory",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.OBSHCHAGA_LINKED);
        player.sendMessage(net.minecraft.text.Text.translatableWithFallback("burmalda.chat.obshchaga.start", "§c[Dorm Room] §fYou're linked with two other players. What happens to you — happens to them."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(net.minecraft.text.Text.translatableWithFallback("burmalda.chat.obshchaga.end", "§a[Dorm Room] §fLink broken."), false);
    }

    // Damage propagation handled by DebuffManager.onPlayerDamaged via PlayerEntityMixin
    // Inventory sync handled by DebuffManager.tickObshchagaLinks every 5 ticks
}
