package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.mixaold.burmalda.sound.BurmaldaSounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DrozhaschieRukiDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 300; // 15 seconds

    public DrozhaschieRukiDebuff() {
        super("drozhaschie_ruki", "Shaking Hands",
                "Are you drunk?",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        // Snapshot all 9 hotbar slots
        List<ItemStack> hotbar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            hotbar.add(player.getInventory().getStack(i).copy());
        }
        // Shuffle and write back
        Collections.shuffle(hotbar, RANDOM);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setStack(i, hotbar.get(i));
        }
        // Force inventory sync so client sees the change
        player.playerScreenHandler.syncState();

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.drozhaschie_ruki.shake", "§e[Shaking Hands] §fWhat's in your hands? Nobody knows."), true);
        BurmaldaSounds.playTune(player);
    }
}
