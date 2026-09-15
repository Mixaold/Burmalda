package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KvantovyRyukzakDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 600; // 30 seconds

    public KvantovyRyukzakDebuff() {
        super("kvantovy_ryukzak", "Quantum Backpack",
                "Items teleport to other players",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<ServerPlayerEntity> others = new ArrayList<>(com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList());
        others.remove(player);
        if (others.isEmpty()) return;

        ServerPlayerEntity target = others.get(RANDOM.nextInt(others.size()));

        // Take up to 3 random items from player's main inventory
        int stolen = 0;
        for (int i = 0; i < 36 && stolen < 3; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            ItemStack drop = stack.copy();
            player.getInventory().setStack(i, ItemStack.EMPTY);
            // Spawn as item entity near target
            ItemEntity item = new ItemEntity(com.mixaold.burmalda.util.BurmaldaCompat.world(target),
                    target.getX(), target.getY() + 0.5, target.getZ(), drop);
            com.mixaold.burmalda.util.BurmaldaCompat.world(target).spawnEntity(item);
            stolen++;
        }

        if (stolen > 0) {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.kvantovy_ryukzak.steal", "§c[Quantum Backpack] §f%s item(s) flew to §e%s", stolen, target.getName().getString()), false);
            target.sendMessage(Text.translatableWithFallback("burmalda.chat.kvantovy_ryukzak.receive", "§c[Quantum Backpack] §fItems appeared near you!"), false);
        }
    }
}
