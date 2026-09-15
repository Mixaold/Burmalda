package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MotygaSudbyDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 100; // every 5 seconds

    // All registered items (including debug sticks, command blocks, barriers, etc.)
    private static final List<Item> ALL_ITEMS = new ArrayList<>();

    private static List<Item> getAllItems() {
        if (ALL_ITEMS.isEmpty()) {
            Registries.ITEM.stream()
                    .filter(item -> item.getMaxCount() > 0)
                    .forEach(ALL_ITEMS::add);
        }
        return ALL_ITEMS;
    }

    public MotygaSudbyDebuff() {
        super("motyga_sudby", "Hoe of Fate",
                "Every 5 seconds — a random item in your inventory",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<Item> items = getAllItems();
        if (items.isEmpty()) return;
        Item randomItem = items.get(RANDOM.nextInt(items.size()));
        int maxStack = Math.max(1, randomItem.getMaxCount());
        int count = maxStack == 1 ? 1 : (1 + RANDOM.nextInt(maxStack));

        ItemStack stack = new ItemStack(randomItem, count);
        String itemName = stack.getName().getString();

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }

        player.sendMessage(net.minecraft.text.Text.translatableWithFallback("burmalda.chat.motyga_sudby.item", "§6[Hoe of Fate] §fHere: §e%s §fx%s", itemName, count), true);
    }
}
