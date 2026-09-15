package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.village.VillagerProfession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class TekhpodderzhkaDebuff extends Debuff {

    private static final int CHAT_INTERVAL = 300; // ~15 s between useless "support" messages
    private static final Random RANDOM = new Random();

    // Per-player state (solo debuff instances are shared between players).
    private final Map<UUID, UUID> specialistByPlayer = new HashMap<>();
    private final Set<UUID> finished = new HashSet<>();

    // Pure flavor — a useless specialist who just follows you and spams the chat. No effects.
    private static final String[] CHATTER_KEYS = {
        "burmalda.chat.tekhpodderzhka.chatter.0", "burmalda.chat.tekhpodderzhka.chatter.1",
        "burmalda.chat.tekhpodderzhka.chatter.2", "burmalda.chat.tekhpodderzhka.chatter.3",
        "burmalda.chat.tekhpodderzhka.chatter.4", "burmalda.chat.tekhpodderzhka.chatter.5",
        "burmalda.chat.tekhpodderzhka.chatter.6", "burmalda.chat.tekhpodderzhka.chatter.7"
    };
    private static final String[] CHATTER_FALLBACKS = {
        "§b[Specialist] §fI see the problem. Now I don't. Now I do again. Okay, observing.",
        "§7[Ticket #48291] §fYour request is being processed. Queue position: 847.",
        "§b[Specialist] §fTry turning yourself off and on again.",
        "§7[Ticket #48291] §fPlease rate our service quality on a scale of 1 to 5.",
        "§b[Specialist] §fThat's not a bug, it's a feature. I'm serious.",
        "§7[Ticket #48291] §fRequest escalated to a senior specialist (that's still me).",
        "§b[Specialist] §fThe system is running stable. So am I — stably useless.",
        "§7[Ticket #48291] §fTicket closed automatically. The problem apparently resolved itself."
    };

    // When the specialist dies, support closes — one of these, then silence forever.
    private static final String[] DEATH_KEYS = {
        "burmalda.chat.tekhpodderzhka.death.0", "burmalda.chat.tekhpodderzhka.death.1",
        "burmalda.chat.tekhpodderzhka.death.2", "burmalda.chat.tekhpodderzhka.death.3",
        "burmalda.chat.tekhpodderzhka.death.4"
    };
    private static final String[] DEATH_FALLBACKS = {
        "§7[Support] §fThe specialist has left the chat. Your ticket was forwarded into the void.",
        "§7[Support] §fConnection to the specialist lost. Average response time: ∞.",
        "§7[Support] §fThe specialist went on lunch break. Forever.",
        "§7[Support] §fYour specialist no longer works here. Thank you for contacting us!",
        "§7[Support] §fSupport session ended with code 137. Have a nice day!"
    };

    public TekhpodderzhkaDebuff() {
        super("tekhpodderzhka", "Tech Support",
                "A specialist has been assigned. He is... present.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        finished.remove(player.getUuid());
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        VillagerEntity villager = new VillagerEntity(net.minecraft.entity.EntityType.VILLAGER, world);
        villager.setPos(player.getX() + 1, player.getY(), player.getZ() + 1);
        //? if >=1.21.5 {
        /*villager.setVillagerData(villager.getVillagerData().withProfession(
                world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION)
                        .getOrThrow(VillagerProfession.NITWIT)));
        *///?} else {
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(VillagerProfession.NITWIT));
        //?}
        villager.setCustomName(Text.translatableWithFallback("burmalda.tekhpodderzhka.specialist_name", "§b[TECH SPEC]"));
        villager.setCustomNameVisible(true);
        villager.setPersistent();
        world.spawnEntity(villager);
        specialistByPlayer.put(player.getUuid(), villager.getUuid());
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.tekhpodderzhka.start",
                "§b[Specialist] §fHello, I'm your specialist. Describe the problem."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (finished.contains(player.getUuid())) return; // specialist dead — total silence

        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        VillagerEntity specialist = findSpecialist(world, player);

        // Death detection — once the specialist is gone, support closes for good.
        if (specialist == null) {
            if (ticksActive > 40) {
                finished.add(player.getUuid());
                int idx = RANDOM.nextInt(DEATH_KEYS.length);
                player.sendMessage(Text.translatableWithFallback(DEATH_KEYS[idx], DEATH_FALLBACKS[idx]), false);
                BurmaldaNetwork.sendDebuffFailed(player); // strike the debuff through
            }
            return;
        }

        // Useless specialist just trails after you...
        if (ticksActive % 10 == 0) {
            specialist.getNavigation().startMovingTo(player, 0.6);
            if (specialist.squaredDistanceTo(player) > 400) {
                specialist.teleport(player.getX() + (RANDOM.nextDouble() - 0.5) * 2,
                        player.getY(), player.getZ() + (RANDOM.nextDouble() - 0.5) * 2, true);
            }
        }

        // ...and periodically spams the chat with helpful nonsense.
        if (ticksActive % CHAT_INTERVAL == 0 && ticksActive > 0) {
            int idx = RANDOM.nextInt(CHATTER_KEYS.length);
            player.sendMessage(Text.translatableWithFallback(CHATTER_KEYS[idx], CHATTER_FALLBACKS[idx]), false);
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        VillagerEntity specialist = findSpecialist(world, player);
        if (specialist != null) {
            specialist.remove(Entity.RemovalReason.DISCARDED);
        }
        specialistByPlayer.remove(player.getUuid());
        finished.remove(player.getUuid());
    }

    private VillagerEntity findSpecialist(ServerWorld world, ServerPlayerEntity player) {
        UUID id = specialistByPlayer.get(player.getUuid());
        if (id == null) return null;
        if (world.getEntity(id) instanceof VillagerEntity v && v.isAlive()) return v;
        return null;
    }
}
