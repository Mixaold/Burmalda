package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class LotereaSmertiDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 1200; // every minute

    public LotereaSmertiDebuff() {
        super("lotereya_smerti", "Death Lottery",
                "Random player — 15 damage, others — gain HP",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        runLottery(server);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL == 0) {
            runLottery(server);
        }
    }

    private void runLottery(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        ServerPlayerEntity victim = players.get(RANDOM.nextInt(players.size()));

        for (ServerPlayerEntity p : players) {
            if (p == victim) {
                com.mixaold.burmalda.util.BurmaldaCompat.damage(p, com.mixaold.burmalda.util.BurmaldaCompat.world(p).getDamageSources().generic(), 15f);
                p.sendMessage(Text.translatableWithFallback("burmalda.chat.lotereya_smerti.loser", "§c[☠ Death Lottery] §fYOU LOST!"), false);
            } else {
                p.setHealth(p.getMaxHealth());
                p.sendMessage(Text.translatableWithFallback("burmalda.chat.lotereya_smerti.winner", "§a[☠ Death Lottery] §fLucky! Sucker is — §e%s", victim.getName().getString()), false);
            }
        }

        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.lotereya_smerti.broadcast", "§c[Burmalda] ☠ §e%s §fdrew death!", victim.getName().getString()), false);
    }
}
