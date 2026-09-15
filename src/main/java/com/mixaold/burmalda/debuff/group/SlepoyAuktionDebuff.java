package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SlepoyAuktionDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    // Snapshot of inventories before swap
    private final List<List<ItemStack>> snapshots = new ArrayList<>();
    private final List<ServerPlayerEntity> swapped = new ArrayList<>();

    public SlepoyAuktionDebuff() {
        super("slepoy_auktsion", "Blind Auction",
                "All player inventories are shuffled",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.size() < 2) return;

        // Snapshot
        snapshots.clear();
        swapped.clear();
        for (ServerPlayerEntity p : players) {
            List<ItemStack> snap = new ArrayList<>();
            for (int i = 0; i < p.getInventory().size(); i++) {
                snap.add(p.getInventory().getStack(i).copy());
            }
            snapshots.add(snap);
            swapped.add(p);
        }

        // Shuffle inventories
        List<List<ItemStack>> shuffled = new ArrayList<>(snapshots);
        Collections.shuffle(shuffled, RANDOM);

        for (int i = 0; i < swapped.size(); i++) {
            ServerPlayerEntity p = swapped.get(i);
            List<ItemStack> newInv = shuffled.get(i);
            for (int j = 0; j < Math.min(p.getInventory().size(), newInv.size()); j++) {
                p.getInventory().setStack(j, newInv.get(j).copy());
            }
        }

        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.slepoy_auktsion.start", "§5[Blind Auction] §fAll inventories shuffled. Those aren't your items."), false);

        // Signal clients that this debuff executed instantly — gray out HUD panel
        for (ServerPlayerEntity p : players) {
            BurmaldaNetwork.sendGroupDebuffDone(p);
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        // Restore original inventories
        for (int i = 0; i < swapped.size(); i++) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(swapped.get(i).getUuid());
            if (p == null) continue;
            List<ItemStack> snap = snapshots.get(i);
            for (int j = 0; j < Math.min(p.getInventory().size(), snap.size()); j++) {
                p.getInventory().setStack(j, snap.get(j).copy());
            }
        }
        snapshots.clear();
        swapped.clear();
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.slepoy_auktsion.end", "§a[Blind Auction] §fInventories restored."), false);
    }
}
