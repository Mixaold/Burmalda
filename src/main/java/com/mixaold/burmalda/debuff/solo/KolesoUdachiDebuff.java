package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.DebuffRegistry;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KolesoUdachiDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int TIMEOUT_TICKS = 1200; // 60 секунд
    private static final int APPLY_DELAY   = 100;  // 5 секунд после получения результата

    /** Per-player wheel state — the debuff is a single shared instance, so all state must be per-player. */
    private static final class Spin {
        String group1Id, group1Name, group2Id, group2Name;
        int     resultSection = -1;
        int     applyAtTick   = -1;
        int     currentTick   = 0;
        boolean played        = false;
    }

    private final Map<UUID, Spin> spins = new ConcurrentHashMap<>();

    public KolesoUdachiDebuff() {
        super("koleso_udachi", "Wheel of Fortune",
                "Spin the wheel. May you be lucky, or not",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        Spin s = new Spin();

        List<Debuff> groups = new ArrayList<>(DebuffRegistry.getGroupDebuffs());
        Collections.shuffle(groups, RANDOM);
        s.group1Id   = groups.get(0).getId();
        s.group1Name = groups.get(0).getName();
        s.group2Id   = groups.get(1).getId();
        s.group2Name = groups.get(1).getName();

        spins.put(player.getUuid(), s);
        BurmaldaNetwork.sendCasinoOpen(player, s.group1Id, s.group1Name, s.group2Id, s.group2Name);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        Spin s = spins.get(player.getUuid());
        if (s == null) return;
        s.currentTick = ticksActive;

        // Отправляем после DebuffAssignedPayload, чтобы HUD остался серым
        if (ticksActive == 1) {
            BurmaldaNetwork.sendDebuffFailed(player);
        }

        // Таймаут 60 сек — если игрок не нажал "Играть"
        if (!s.played && ticksActive >= TIMEOUT_TICKS) {
            s.played = true;
            BurmaldaNetwork.sendCasinoTimeout(player);
            // Убиваем через 8 секунд: 5с анимация + 3с карточка результата
            s.resultSection = 6; // смерть
            s.applyAtTick   = ticksActive + 160;
        }

        if (s.applyAtTick > 0 && ticksActive >= s.applyAtTick) {
            applyResult(player, s);
            s.applyAtTick = -1;
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        spins.remove(player.getUuid());
    }

    /** Вызывается из packet handler'а на сервере, когда игрок нажал "Играть". */
    public void onPlayerSpin(ServerPlayerEntity player) {
        Spin s = spins.get(player.getUuid());
        if (s == null || s.played) return;
        s.played        = true;
        s.resultSection = RANDOM.nextInt(7);
        BurmaldaNetwork.sendCasinoResult(player, s.resultSection);
        s.applyAtTick = s.currentTick + APPLY_DELAY;
    }

    // ─── Применение результата ────────────────────────────────────────────────

    private void applyResult(ServerPlayerEntity player, Spin s) {
        MinecraftServer server = com.mixaold.burmalda.util.BurmaldaCompat.server(player);
        if (server == null) return;

        switch (s.resultSection) {
            case 0 -> {
                DebuffManager.skipNextSoloFor(player.getUuid());
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.koleso_udachi.skip_self",
                        "§a[🎰 Wheel of Fortune] §fThe wheel decided: next debuff skipped!"), false);
            }
            case 1 -> {
                DebuffManager.skipNextSoloForAll();
                server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.koleso_udachi.skip_all",
                        "§a[🎰 Wheel of Fortune] §fThe wheel spared everyone — next cycle is debuff-free!"), false);
            }
            case 2 -> {
                giveNetherite(player);
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.koleso_udachi.netherite",
                        "§b[🎰 Wheel of Fortune] §fThe wheel is generous... Netherite set is yours."), false);
            }
            case 3 -> {
                server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.koleso_udachi.all_die",
                        "§c[🎰 Wheel of Fortune] §fThe wheel is evil. Everyone dies."), false);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    com.mixaold.burmalda.util.BurmaldaCompat.damage(p, server.getOverworld().getDamageSources().outOfWorld(), Float.MAX_VALUE);
                }
            }
            case 4 -> {
                Debuff g1 = DebuffRegistry.getGroupById(s.group1Id);
                if (g1 != null) {
                    server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.koleso_udachi.group",
                            "§d[🎰 Wheel of Fortune] §fThe wheel chose: §d%s", g1.getName()), false);
                    DebuffManager.forceAssignGroup(server, g1);
                }
            }
            case 5 -> {
                Debuff g2 = DebuffRegistry.getGroupById(s.group2Id);
                if (g2 != null) {
                    server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.koleso_udachi.group",
                            "§d[🎰 Wheel of Fortune] §fThe wheel chose: §d%s", g2.getName()), false);
                    DebuffManager.forceAssignGroup(server, g2);
                }
            }
            case 6 -> {
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.koleso_udachi.death",
                        "§4[🎰 Wheel of Fortune] §cThe wheel said no."), false);
                com.mixaold.burmalda.util.BurmaldaCompat.kill(player);
            }
        }
    }

    private void giveNetherite(ServerPlayerEntity player) {
        player.equipStack(EquipmentSlot.HEAD,  new ItemStack(Items.NETHERITE_HELMET));
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS,  new ItemStack(Items.NETHERITE_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET,  new ItemStack(Items.NETHERITE_BOOTS));
        player.giveItemStack(new ItemStack(Items.NETHERITE_SWORD));
        player.giveItemStack(new ItemStack(Items.NETHERITE_PICKAXE));
        player.giveItemStack(new ItemStack(Items.NETHERITE_AXE));
        player.giveItemStack(new ItemStack(Items.NETHERITE_SHOVEL));
        player.giveItemStack(new ItemStack(Items.NETHERITE_HOE));
        player.giveItemStack(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
    }
}
