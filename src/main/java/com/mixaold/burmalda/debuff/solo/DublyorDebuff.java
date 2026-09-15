package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DublyorDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int SPAWN_INTERVAL = 600; // spawn every 30 sec

    private final List<Integer> zombieIds = new ArrayList<>();

    public DublyorDebuff() {
        super("dublyor", "Doppelganger",
                "Zombie clones wearing your gear — you, but angrier",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        zombieIds.clear();
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        for (int id : zombieIds) {
            var e = world.getEntityById(id);
            if (e != null) e.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
        zombieIds.clear();
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % SPAWN_INTERVAL == 0) {
            spawnDuplicate(player, com.mixaold.burmalda.util.BurmaldaCompat.world(player));
        }
    }

    private void spawnDuplicate(ServerPlayerEntity player, ServerWorld world) {
        ZombieEntity zombie = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ZOMBIE, world);
        if (zombie == null) return;

        double ox = (RANDOM.nextDouble() - 0.5) * 10;
        double oz = (RANDOM.nextDouble() - 0.5) * 10;
        zombie.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
        zombie.setCustomName(Text.literal("§c" + player.getName().getString() + " §7(ДВОЙНИК)"));
        zombie.setCustomNameVisible(true);
        zombie.setGlowing(true);
        zombie.setPersistent();

        // Copy player's equipment — force 100% drop chance per slot
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                zombie.equipStack(slot, stack.copy());
                zombie.setEquipmentDropChance(slot, 1.0f);
            }
        }

        // Strength I so it actually threatens people
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 0, false, false));

        // Target a random other player
        List<ServerPlayerEntity> others = new ArrayList<>(com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList());
        others.remove(player);
        if (!others.isEmpty()) zombie.setTarget(others.get(RANDOM.nextInt(others.size())));

        world.spawnEntity(zombie);
        zombieIds.add(zombie.getId());

        // Cap at 2 active duplicates — remove oldest if over limit
        if (zombieIds.size() > 2) {
            var old = world.getEntityById(zombieIds.remove(0));
            if (old != null) old.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.dublyor.spawn", "§c[Doppelganger] §fIt's here. And it's angry."), true);
    }
}
