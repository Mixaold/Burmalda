package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Random;

public class KonfettiDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 100; // every 5 seconds

    private static final List<ItemStack> JUNK = List.of(
            new ItemStack(Items.DIRT, 8),
            new ItemStack(Items.GRAVEL, 4),
            new ItemStack(Items.ROTTEN_FLESH, 3),
            new ItemStack(Items.SAND, 6),
            new ItemStack(Items.COBBLESTONE, 4),
            new ItemStack(Items.POINTED_DRIPSTONE),
            new ItemStack(Items.DEAD_BUSH),
            new ItemStack(Items.BONE, 2),
            new ItemStack(Items.FEATHER, 3),
            new ItemStack(Items.PAPER, 5)
    );

    public KonfettiDebuff() {
        super("konfetti", "Confetti",
                "Junk fills your inventory every 5 seconds",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack junk = JUNK.get(RANDOM.nextInt(JUNK.size())).copy();
            if (!player.getInventory().insertStack(junk)) {
                // If full, drop it
                player.dropItem(junk, false);
            }
        }
    }
}
