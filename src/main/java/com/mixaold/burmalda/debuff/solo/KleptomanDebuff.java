package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KleptomanDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 800; // 40 seconds

    public KleptomanDebuff() {
        super("kleptoman", "Kleptomaniac",
                "You steal from yourself and give to others",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<ServerPlayerEntity> others = new ArrayList<>(com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList());
        others.remove(player);
        if (others.isEmpty()) return;

        // Pick a random non-empty slot
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (!player.getInventory().getStack(i).isEmpty()) nonEmpty.add(i);
        }
        if (nonEmpty.isEmpty()) return;

        int slot = nonEmpty.get(RANDOM.nextInt(nonEmpty.size()));
        ItemStack stolen = player.getInventory().getStack(slot).copy();
        player.getInventory().setStack(slot, ItemStack.EMPTY);

        ServerPlayerEntity target = others.get(RANDOM.nextInt(others.size()));
        ItemEntity entity = new ItemEntity(com.mixaold.burmalda.util.BurmaldaCompat.world(target),
                target.getX(), target.getY() + 0.5, target.getZ(), stolen);
        com.mixaold.burmalda.util.BurmaldaCompat.world(target).spawnEntity(entity);

        BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.KLEPTO_VICTIM);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.kleptoman.steal", "§c[Kleptomaniac] §f«%s» flew to §e%s", stolen.getName().getString(), target.getName().getString()), false);
    }
}
