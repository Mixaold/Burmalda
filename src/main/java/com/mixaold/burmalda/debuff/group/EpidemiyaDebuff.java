package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class EpidemiyaDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int  INTERVAL     = 40;   // check every 2 s
    private static final double SPREAD_RANGE = 5.0; // blocks

    private final Set<UUID> infected = new HashSet<>();

    public EpidemiyaDebuff() {
        super("epidemiya", "Epidemic",
                "Infected player spreads effects to nearby players",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        infected.clear();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        ServerPlayerEntity zero = players.get(RANDOM.nextInt(players.size()));
        infected.add(zero.getUuid());
        applyInfection(zero);

        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.epidemiya.patient_zero", "§2[Epidemic] §e%s §f— patient zero! Stay away!", zero.getName().getString()), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        for (UUID uuid : new ArrayList<>(infected)) {
            ServerPlayerEntity src = server.getPlayerManager().getPlayer(uuid);
            if (src == null) continue;

            // Keep infection active on already-infected players
            applyInfection(src);

            // Spread to nearby healthy players
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (infected.contains(other.getUuid())) continue;
                if (src.distanceTo(other) <= SPREAD_RANGE) {
                    infected.add(other.getUuid());
                    applyInfection(other);
                    server.getPlayerManager().broadcast(
                            Text.translatableWithFallback("burmalda.chat.epidemiya.infected", "§2[Epidemic] §e%s §fis infected!", other.getName().getString()), false);
                    other.sendMessage(
                            Text.translatableWithFallback("burmalda.chat.epidemiya.warning", "§2[Epidemic] §cYou got too close to an infected player!"), true);
                }
            }
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        for (UUID uuid : infected) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p == null) continue;
            p.removeStatusEffect(StatusEffects.POISON);
            p.removeStatusEffect(StatusEffects.NAUSEA);
            p.removeStatusEffect(StatusEffects.SLOWNESS);
            p.removeStatusEffect(StatusEffects.GLOWING);
        }
        infected.clear();
    }

    private static void applyInfection(ServerPlayerEntity player) {
        // 3 seconds duration, refreshed every 2 s tick → continuous
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON,   60, 0, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,   60, 0, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, false, true));
        // Glow so others can see infected players through walls
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,  60, 0, false, false));
    }
}
